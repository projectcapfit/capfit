package com.jntuh.capfit.ui.friends

import android.graphics.Color
import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import android.view.View
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.jntuh.capfit.adapter.FriendsAdapter
import com.jntuh.capfit.databinding.ActivityFriendsBinding
import com.jntuh.capfit.viewmodel.UserGameDataViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class Friends : AppCompatActivity() {

    private lateinit var binding: ActivityFriendsBinding
    private val viewModel: UserGameDataViewModel by viewModels()

    private lateinit var adapter: FriendsAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.statusBarColor = Color.TRANSPARENT
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = true
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_STABLE

        binding = ActivityFriendsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupRecycler()
        observeFriends()

        viewModel.loadFriends()

        binding.btnBack.setOnClickListener { finish() }

        setupSearch()
    }

    // ---------------- Recycler ----------------

    private fun setupRecycler() {
        adapter = FriendsAdapter(
            onAddClick = { user ->
//                viewModel.addFriend(user.uid)
                viewModel.sendFriendRequest(user.uid)
            },
            onRemoveClick = { user ->
                viewModel.removeFriend(user.uid)
            }
        )

        binding.recyclerViewSearchResults.layoutManager =
            LinearLayoutManager(this)

        binding.recyclerViewSearchResults.adapter = adapter
    }

    // ---------------- Observe Friends ----------------

    private fun observeFriends() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.friends.collect {
                    adapter.setMode(FriendsAdapter.Mode.FRIENDS)
                    adapter.submitList(it)
                }
            }
        }
    }

    // ---------------- Observe Search ----------------

    private fun observeSearch() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.searchResults.collect {
                    adapter.setMode(FriendsAdapter.Mode.SEARCH)
                    adapter.submitList(it)
                }
            }
        }
    }

    // ---------------- Search ----------------

    private fun setupSearch() {

        binding.editSearchFriends.addTextChangedListener {

            val query = it.toString()

            if (query.isBlank()) {
                viewModel.loadFriends()
                observeFriends()
            } else {
                viewModel.searchUsers(query)
                observeSearch()
            }
        }
    }
}
