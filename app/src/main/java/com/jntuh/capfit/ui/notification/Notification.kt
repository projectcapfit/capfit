package com.jntuh.capfit.ui.notification

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.jntuh.capfit.adapter.NotificationAdapter
import com.jntuh.capfit.data.AppNotification
import com.jntuh.capfit.data.NotificationType
import com.jntuh.capfit.databinding.ActivityNotificationBinding
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class Notification : AppCompatActivity() {

    private lateinit var binding: ActivityNotificationBinding

    @Inject lateinit var auth: FirebaseAuth
    @Inject lateinit var firestore: FirebaseFirestore

    private val notifications = mutableListOf<AppNotification>()
    private lateinit var adapter: NotificationAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        binding = ActivityNotificationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupRecycler()
        setupBackButton()
        listenToNotifications()
    }

    private fun setupBackButton() {
        binding.btnBack.setOnClickListener { finish() }
    }

    private fun setupRecycler() {
        adapter = NotificationAdapter(
            notifications = notifications,
            onAcceptClicked = { notif -> acceptFriendRequest(notif) },
            onRejectClicked = { notif -> rejectFriendRequest(notif) }
        )

        binding.rvNotifications.layoutManager = LinearLayoutManager(this)
        binding.rvNotifications.adapter = adapter
    }

    private fun listenToNotifications() {
        val uid = auth.currentUser?.uid ?: return

        firestore.collection("users")
            .document(uid)
            .collection("notifications")
            .orderBy("timestamp")
            .addSnapshotListener { snap, error ->
                if (error != null || snap == null) return@addSnapshotListener

                notifications.clear()
                for (doc in snap.documents) {
                    doc.toObject(AppNotification::class.java)?.let { notifications.add(it) }
                }

                adapter.notifyDataSetChanged()
            }
    }

    private fun acceptFriendRequest(notification: AppNotification) {

        val currentUid = auth.currentUser?.uid ?: return
        val fromUid = notification.fromUserId ?: return

        val currentRef = firestore.collection("users").document(currentUid)
        val fromRef = firestore.collection("users").document(fromUid)
        val notifRef = currentRef.collection("notifications").document(notification.id)

        firestore.runBatch { batch ->

            batch.update(currentRef, "friendsList",
                com.google.firebase.firestore.FieldValue.arrayUnion(fromUid))

            batch.update(fromRef, "friendsList",
                com.google.firebase.firestore.FieldValue.arrayUnion(currentUid))

            batch.update(notifRef, mapOf(
                "actionStatus" to "ACCEPTED",
                "isRead" to true
            ))
        }.addOnSuccessListener {
            sendFriendAcceptedNotification(fromUid)
        }
    }

    private fun rejectFriendRequest(notification: AppNotification) {

        val currentUid = auth.currentUser?.uid ?: return

        val notifRef = firestore.collection("users")
            .document(currentUid)
            .collection("notifications")
            .document(notification.id)

        notifRef.update(
            mapOf(
                "actionStatus" to "REJECTED",
                "isRead" to true
            )
        )
    }

    private fun sendFriendAcceptedNotification(receiverUid: String) {

        val currentUid = auth.currentUser?.uid ?: return
        val newId = firestore.collection("x").document().id

        val payload = hashMapOf(
            "id" to newId,
            "type" to NotificationType.FRIEND_ACCEPTED.name,
            "title" to "Friend Request Accepted",
            "message" to "Your friend request was accepted!",
            "fromUserId" to currentUid,
            "timestamp" to System.currentTimeMillis(),
            "isRead" to false,
            "actionStatus" to "NONE"
        )

        firestore.collection("users")
            .document(receiverUid)
            .collection("notifications")
            .document(newId)
            .set(payload)
    }


    private fun markAllAsRead() {
        val uid = auth.currentUser?.uid ?: return

        val notifRef = firestore.collection("users")
            .document(uid)
            .collection("notifications")

        firestore.runBatch { batch ->
            notifications.forEach { notif ->
                val ref = notifRef.document(notif.id)
                batch.update(ref, "isRead", true)
            }
        }
    }
    override fun onResume() {
        super.onResume()
        markAllAsRead()
    }
}
