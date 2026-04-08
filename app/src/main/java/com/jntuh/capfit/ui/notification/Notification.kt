package com.jntuh.capfit.ui.notification

import android.graphics.Color
import android.os.Bundle
import android.util.Log
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowInsetsControllerCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.jntuh.capfit.adapter.NotificationAdapter
import com.jntuh.capfit.data.AppNotification
import com.jntuh.capfit.data.NotificationType
import android.view.View
import com.jntuh.capfit.databinding.ActivityNotificationBinding
import com.jntuh.capfit.viewmodel.UserGameDataViewModel
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlin.getValue

@AndroidEntryPoint
class Notification : AppCompatActivity() {

    private lateinit var binding: ActivityNotificationBinding

    @Inject lateinit var auth: FirebaseAuth
    @Inject lateinit var firestore: FirebaseFirestore

    private val userGameViewModel: UserGameDataViewModel by viewModels()

    private val notifications = mutableListOf<AppNotification>()
    private lateinit var adapter: NotificationAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.statusBarColor = Color.TRANSPARENT
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = true
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_STABLE

        binding = ActivityNotificationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupRecycler()
        setupBackButton()
        listenToNotifications()
    }

    private fun setupBackButton() {
        binding.btnBack.setOnClickListener {
            finish()
        }
    }

    private fun setupRecycler() {
        adapter = NotificationAdapter(
            notifications = notifications,
            onAcceptClicked = { notif ->
                acceptFriendRequest(notif)
            },
            onRejectClicked = { notif ->
                rejectFriendRequest(notif)
            }
        )

        binding.rvNotifications.layoutManager = LinearLayoutManager(this)
        binding.rvNotifications.adapter = adapter
    }

    private var listener: ListenerRegistration? = null

    private fun listenToNotifications() {

        val uid = auth.currentUser?.uid ?: run {
            return
        }


        listener = firestore.collection("userGameData")
            .document(uid)
            .collection("notifications")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snap, error ->

                if (error != null) {
                    return@addSnapshotListener
                }

                if (snap == null) {
                    return@addSnapshotListener
                }

                notifications.clear()

                for (doc in snap.documents) {
                    val obj = doc.toObject(AppNotification::class.java)
                    obj?.let { notifications.add(it) }
                }

                adapter.notifyDataSetChanged()
            }
    }

    override fun onDestroy() {
        super.onDestroy()
        listener?.remove()
    }

    // ---------------- ACCEPT ----------------

    private fun acceptFriendRequest(notification: AppNotification) {

        val currentUid = auth.currentUser?.uid ?: run {
            return
        }

        val fromUid = notification.fromUserId ?: run {
            return
        }


        val currentRef = firestore.collection("userGameData").document(currentUid)
        val fromRef = firestore.collection("userGameData").document(fromUid)

        val notifRef = currentRef
            .collection("notifications")
            .document(notification.id)

        firestore.runBatch { batch ->

            batch.set(
                currentRef,
                mapOf("friendsList" to
                        com.google.firebase.firestore.FieldValue.arrayUnion(fromUid)),
                com.google.firebase.firestore.SetOptions.merge()
            )

            batch.set(
                fromRef,
                mapOf("friendsList" to
                        com.google.firebase.firestore.FieldValue.arrayUnion(currentUid)),
                com.google.firebase.firestore.SetOptions.merge()
            )

            batch.update(notifRef, mapOf(
                "actionStatus" to "ACCEPTED",
                "isRead" to true
            ))
        }
            .addOnSuccessListener {
                userGameViewModel.loadFriends()
                sendFriendAcceptedNotification(fromUid)
            }
    }

    // ---------------- REJECT ----------------

    private fun rejectFriendRequest(notification: AppNotification) {

        val currentUid = auth.currentUser?.uid ?: return


        val notifRef = firestore.collection("userGameData")
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

    // ---------------- SEND ACCEPTED NOTIF ----------------

    private fun sendFriendAcceptedNotification(receiverUid: String) {

        val currentUid = auth.currentUser?.uid ?: return


        val notifRef = firestore.collection("userGameData")
            .document(receiverUid)
            .collection("notifications")
            .document()

        val payload = hashMapOf(
            "id" to notifRef.id,
            "type" to NotificationType.FRIEND_ACCEPTED.name,
            "title" to "Friend Request Accepted",
            "message" to "Your friend request was accepted!",
            "fromUserId" to currentUid,
            "timestamp" to System.currentTimeMillis(),
            "isRead" to false,
            "actionStatus" to "NONE"
        )

        notifRef.set(payload)
    }

    // ---------------- MARK READ ----------------

    private fun markAllAsRead() {

        val uid = auth.currentUser?.uid ?: return


        val notifRef = firestore.collection("userGameData")
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