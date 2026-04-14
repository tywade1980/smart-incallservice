package com.aireceptionist.app.ui.training

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.aireceptionist.app.data.models.FAQEntry
import com.aireceptionist.app.databinding.ActivityTrainingBinding
import com.aireceptionist.app.databinding.ItemFaqEntryBinding
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/**
 * AI Training screen – lets business owners manage FAQs that the AI uses to
 * answer caller questions. Also provides a “Train AI” action to re-index
 * all active FAQs into the knowledge base.
 */
@AndroidEntryPoint
class TrainingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTrainingBinding
    private val viewModel: TrainingViewModel by viewModels()
    private lateinit var adapter: FAQAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTrainingBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            title = "AI Training"
        }
        setupRecyclerView()
        setupUI()
        observeViewModel()
    }

    private fun setupRecyclerView() {
        adapter = FAQAdapter(
            onDelete = { entry -> viewModel.deleteFAQ(entry) },
            onToggle = { entry -> viewModel.toggleFAQ(entry) }
        )
        binding.rvFaqs.apply {
            this.adapter = this@TrainingActivity.adapter
            layoutManager = LinearLayoutManager(this@TrainingActivity)
        }
    }

    private fun setupUI() {
        binding.btnAddFaq.setOnClickListener {
            val q    = binding.etQuestion.text.toString().trim()
            val a    = binding.etAnswer.text.toString().trim()
            val cat  = binding.etCategory.text.toString().trim().ifBlank { "General" }
            if (q.isBlank() || a.isBlank()) {
                Snackbar.make(binding.root, "Question and answer are required", Snackbar.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            viewModel.addFAQ(q, a, cat)
            binding.etQuestion.text?.clear()
            binding.etAnswer.text?.clear()
            binding.etCategory.text?.clear()
        }

        binding.btnTrainNow.setOnClickListener {
            viewModel.runTrainingPass()
        }
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {

                launch {
                    viewModel.faqs.collect { list ->
                        adapter.submitList(list)
                        binding.tvFaqCount.text = "${list.size} FAQ${if (list.size != 1) "s" else ""}"
                    }
                }

                launch {
                    viewModel.trainingStatus.collect { status ->
                        binding.tvTrainingStatus.text = status
                    }
                }

                launch {
                    viewModel.isTraining.collect { busy ->
                        binding.progressTraining.visibility = if (busy) View.VISIBLE else View.GONE
                        binding.btnTrainNow.isEnabled = !busy
                    }
                }
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }

    // ---------------------------------------------------------------
    // RecyclerView adapter
    // ---------------------------------------------------------------

    class FAQAdapter(
        private val onDelete: (FAQEntry) -> Unit,
        private val onToggle: (FAQEntry) -> Unit
    ) : ListAdapter<FAQEntry, FAQAdapter.VH>(DiffCB()) {

        inner class VH(val b: ItemFaqEntryBinding) : RecyclerView.ViewHolder(b.root) {
            fun bind(entry: FAQEntry) {
                b.tvQuestion.text   = entry.question
                b.tvAnswer.text     = entry.answer
                b.tvCategory.text   = entry.category
                b.switchActive.isChecked = entry.isActive
                b.switchActive.setOnCheckedChangeListener { _, _ -> onToggle(entry) }
                b.btnDelete.setOnClickListener { onDelete(entry) }
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = VH(
            ItemFaqEntryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        )

        override fun onBindViewHolder(holder: VH, position: Int) {
            holder.bind(getItem(position))
        }

        class DiffCB : DiffUtil.ItemCallback<FAQEntry>() {
            override fun areItemsTheSame(a: FAQEntry, b: FAQEntry) = a.id == b.id
            override fun areContentsTheSame(a: FAQEntry, b: FAQEntry) = a == b
        }
    }
}
