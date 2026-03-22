package com.jntuh.capfit.ui.notification

import android.os.Bundle
import android.util.Log
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.jntuh.capfit.adapter.NotificationAdapter
import com.jntuh.capfit.data.AppNotification
import com.jntuh.capfit.data.NotificationType
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
        enableEdgeToEdge()

        Log.v("asasas","Notification screen opened")

        binding = ActivityNotificationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupRecycler()
        setupBackButton()
        listenToNotifications()
    }

    private fun setupBackButton() {
        binding.btnBack.setOnClickListener {
            Log.v("asasas","Back pressed")
            finish()
        }
    }

    private fun setupRecycler() {
        Log.v("asasas","Recycler setup")

        adapter = NotificationAdapter(
            notifications = notifications,
            onAcceptClicked = { notif ->
                Log.v("asasas","Accept clicked for ${notif.id}")
                acceptFriendRequest(notif)
            },
            onRejectClicked = { notif ->
                Log.v("asasas","Reject clicked for ${notif.id}")
                rejectFriendRequest(notif)
            }
        )

        binding.rvNotifications.layoutManager = LinearLayoutManager(this)
        binding.rvNotifications.adapter = adapter
    }

    private var listener: ListenerRegistration? = null

    private fun listenToNotifications() {

        val uid = auth.currentUser?.uid ?: run {
            Log.v("asasas","listenToNotifications → UID NULL")
            return
        }

        Log.v("asasas","Listening notifications for $uid")

        listener = firestore.collection("userGameData")
            .document(uid)
            .collection("notifications")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snap, error ->

                if (error != null) {
                    Log.v("asasas","Snapshot error ${error.message}")
                    return@addSnapshotListener
                }

                if (snap == null) {
                    Log.v("asasas","Snapshot null")
                    return@addSnapshotListener
                }

                Log.v("asasas","Snapshot received size=${snap.size()}")

                notifications.clear()

                for (doc in snap.documents) {
                    val obj = doc.toObject(AppNotification::class.java)
                    Log.v("asasas","Notification doc=${doc.id} data=$obj")
                    obj?.let { notifications.add(it) }
                }

                adapter.notifyDataSetChanged()
            }
    }

    override fun onDestroy() {
        super.onDestroy()
        listener?.remove()
        Log.v("asasas","Listener removed")
    }

    // ---------------- ACCEPT ----------------

    private fun acceptFriendRequest(notification: AppNotification) {

        val currentUid = auth.currentUser?.uid ?: run {
            Log.v("asasas","Accept failed → currentUid null")
            return
        }

        val fromUid = notification.fromUserId ?: run {
            Log.v("asasas","Accept failed → fromUid null")
            return
        }

        Log.v("asasas","Accepting friend request from $fromUid to $currentUid")

        val currentRef = firestore.collection("userGameData").document(currentUid)
        val fromRef = firestore.collection("userGameData").document(fromUid)

        val notifRef = currentRef
            .collection("notifications")
            .document(notification.id)

        firestore.runBatch { batch ->

            Log.v("asasas","Updating friendsList both users")

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
                Log.v("asasas","Friend added successfully")
                userGameViewModel.loadFriends()
                sendFriendAcceptedNotification(fromUid)
            }
            .addOnFailureListener {
                Log.v("asasas","Batch failed ${it.message}")
            }
    }

    // ---------------- REJECT ----------------

    private fun rejectFriendRequest(notification: AppNotification) {

        val currentUid = auth.currentUser?.uid ?: return

        Log.v("asasas","Rejecting request ${notification.id}")

        val notifRef = firestore.collection("userGameData")
            .document(currentUid)
            .collection("notifications")
            .document(notification.id)

        notifRef.update(
            mapOf(
                "actionStatus" to "REJECTED",
                "isRead" to true
            )
        ).addOnSuccessListener {
            Log.v("asasas","Request rejected")
        }.addOnFailureListener {
            Log.v("asasas","Reject failed ${it.message}")
        }
    }

    // ---------------- SEND ACCEPTED NOTIF ----------------

    private fun sendFriendAcceptedNotification(receiverUid: String) {

        val currentUid = auth.currentUser?.uid ?: return

        Log.v("asasas","Sending accepted notification to $receiverUid")

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
            .addOnSuccessListener {
                Log.v("asasas","Accepted notification sent")
            }
            .addOnFailureListener {
                Log.v("asasas","Accepted notification failed ${it.message}")
            }
    }

    // ---------------- MARK READ ----------------

    private fun markAllAsRead() {

        val uid = auth.currentUser?.uid ?: return

        Log.v("asasas","Marking all notifications read")

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
        Log.v("asasas","Notification screen resumed")
        markAllAsRead()
    }
}