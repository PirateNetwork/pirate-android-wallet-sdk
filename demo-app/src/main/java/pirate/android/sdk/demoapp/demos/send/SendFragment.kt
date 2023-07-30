package pirate.android.sdk.demoapp.demos.send

import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import pirate.android.sdk.PirateSynchronizer
import pirate.android.sdk.block.PirateCompactBlockProcessor
import pirate.android.sdk.demoapp.BaseDemoFragment
import pirate.android.sdk.demoapp.DemoConstants
import pirate.android.sdk.demoapp.R
import pirate.android.sdk.demoapp.databinding.FragmentSendBinding
import pirate.android.sdk.demoapp.util.mainActivity
import pirate.android.sdk.ext.collectWith
import pirate.android.sdk.ext.convertArrrtoshiToArrrString
import pirate.android.sdk.ext.convertArrrToArrrtoshi
import pirate.android.sdk.ext.toArrrString
import pirate.android.sdk.internal.Twig
import pirate.android.sdk.internal.twig
import pirate.android.sdk.model.PendingTransaction
import pirate.android.sdk.model.PirateUnifiedSpendingKey
import pirate.android.sdk.model.PirateWalletBalance
import pirate.android.sdk.model.isCreated
import pirate.android.sdk.model.isCreating
import pirate.android.sdk.model.isFailedEncoding
import pirate.android.sdk.model.isFailedSubmit
import pirate.android.sdk.model.isMined
import pirate.android.sdk.model.isSubmitSuccess
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch

/**
 * Demonstrates sending funds to an address. This is the most complex example that puts all of the
 * pieces of the SDK together, including monitoring transactions for completion. It begins by
 * downloading, validating and scanning any missing blocks. Once that is complete, the wallet is
 * in a SYNCED state and available to send funds. Calling `sendToAddress` produces a flow of
 * PendingTransaction objects which represent the active state of the transaction that was sent.
 * Any time the state of that transaction changes, a new instance will be emitted.
 */
@Suppress("TooManyFunctions")
class SendFragment : BaseDemoFragment<FragmentSendBinding>() {

    private lateinit var amountInput: TextView
    private lateinit var addressInput: TextView

    // in a normal app, this would be stored securely with the trusted execution environment (TEE)
    // but since this is a demo, we'll derive it on the fly
    private lateinit var spendingKey: PirateUnifiedSpendingKey

    //
    // Observable properties (done without livedata or flows for simplicity)
    //

    private var balance: PirateWalletBalance? = null
        set(value) {
            field = value
            onUpdateSendButton()
        }
    private var isSending = false
        set(value) {
            field = value
            if (value) Twig.sprout("Sending") else Twig.clip("Sending")
            onUpdateSendButton()
        }
    private var isSyncing = true
        set(value) {
            field = value
            onUpdateSendButton()
        }

    //
    // Private functions
    //

    private fun initSendUi() {
        amountInput = binding.inputAmount.apply {
            setText(DemoConstants.SEND_AMOUNT.toArrrString())
        }
        addressInput = binding.inputAddress.apply {
            setText(DemoConstants.TO_ADDRESS)
        }
        binding.buttonSend.setOnClickListener(::onSend)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun monitorChanges() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    sharedViewModel.synchronizerFlow
                        .filterNotNull()
                        .flatMapLatest { it.status }
                        .collect { onStatus(it) }
                }
                launch {
                    sharedViewModel.synchronizerFlow
                        .filterNotNull()
                        .flatMapLatest { it.progress }
                        .collect { onProgress(it) }
                }
                launch {
                    sharedViewModel.synchronizerFlow
                        .filterNotNull()
                        .flatMapLatest { it.processorInfo }
                        .collect { onProcessorInfoUpdated(it) }
                }
                launch {
                    sharedViewModel.synchronizerFlow
                        .filterNotNull()
                        .flatMapLatest { it.saplingBalances }
                        .collect { onBalance(it) }
                }
            }
        }
    }

    //
    // Change listeners
    //

    private fun onStatus(status: PirateSynchronizer.PirateStatus) {
        binding.textStatus.text = "Status: $status"
        isSyncing = status != PirateSynchronizer.PirateStatus.SYNCED
        if (status == PirateSynchronizer.PirateStatus.SCANNING) {
            binding.textBalance.text = "Calculating balance..."
        } else {
            if (!isSyncing) onBalance(balance)
        }
    }

    @Suppress("MagicNumber")
    private fun onProgress(i: Int) {
        if (i < 100) {
            binding.textStatus.text = "Downloading blocks...$i%"
            binding.textBalance.visibility = View.INVISIBLE
        } else {
            binding.textBalance.visibility = View.VISIBLE
        }
    }

    private fun onProcessorInfoUpdated(info: PirateCompactBlockProcessor.ProcessorInfo) {
        if (info.isScanning) binding.textStatus.text = "Scanning blocks...${info.scanProgress}%"
    }

    @Suppress("MagicNumber")
    private fun onBalance(balance: PirateWalletBalance?) {
        this.balance = balance
        if (!isSyncing) {
            binding.textBalance.text = """
                Available balance: ${balance?.available.convertArrrtoshiToArrrString(12)}
                Total balance: ${balance?.total.convertArrrtoshiToArrrString(12)}
            """.trimIndent()
        }
    }

    @Suppress("UNUSED_PARAMETER")
    private fun onSend(unused: View) {
        isSending = true
        val amount = amountInput.text.toString().toDouble().convertArrrToArrrtoshi()
        val toAddress = addressInput.text.toString().trim()
        lifecycleScope.launch {
            sharedViewModel.synchronizerFlow.value?.sendToAddress(
                spendingKey,
                amount,
                toAddress,
                "Funds from Demo App"
            )?.collectWith(lifecycleScope, ::onPendingTxUpdated)
        }

        mainActivity()?.hideKeyboard()
    }

    @Suppress("ComplexMethod")
    private fun onPendingTxUpdated(pendingTransaction: PendingTransaction?) {
        val message = when {
            pendingTransaction == null -> "Transaction not found"
            pendingTransaction.isMined() -> "Transaction Mined!\n\nSEND COMPLETE".also { isSending = false }
            pendingTransaction.isSubmitSuccess() -> "Successfully submitted transaction!\nAwaiting confirmation..."
            pendingTransaction.isFailedEncoding() ->
                "ERROR: failed to encode transaction!".also { isSending = false }
            pendingTransaction.isFailedSubmit() ->
                "ERROR: failed to submit transaction!".also { isSending = false }
            pendingTransaction.isCreated() -> "Transaction creation complete! (id: $id)"
            pendingTransaction.isCreating() -> "Creating transaction!".also { onResetInfo() }
            else -> "Transaction updated!".also { twig("Unhandled TX state: $pendingTransaction") }
        }
        twig("Pending TX Updated: $message")
        binding.textInfo.apply {
            text = "$text\n$message"
        }
    }

    private fun onUpdateSendButton() {
        with(binding.buttonSend) {
            when {
                isSending -> {
                    text = "➡ sending"
                    isEnabled = false
                }
                isSyncing -> {
                    text = "⌛ syncing"
                    isEnabled = false
                }
                (balance?.available?.value ?: 0) <= 0 -> isEnabled = false
                else -> {
                    text = "send"
                    isEnabled = true
                }
            }
        }
    }

    private fun onResetInfo() {
        binding.textInfo.text = "Active Transaction:"
    }

    //
    // Android Lifecycle overrides
    //

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = super.onCreateView(inflater, container, savedInstanceState)
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initSendUi()
        monitorChanges()
    }

    override fun onPrepareOptionsMenu(menu: Menu) {
        super.onPrepareOptionsMenu(menu)
        // We rather hide options menu actions while actively using the PirateSynchronizer
        menu.setGroupVisible(R.id.main_menu_group, false)
    }

    //
    // BaseDemoFragment overrides
    //

    override fun inflateBinding(layoutInflater: LayoutInflater): FragmentSendBinding =
        FragmentSendBinding.inflate(layoutInflater)
}
