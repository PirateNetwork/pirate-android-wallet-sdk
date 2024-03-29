package pirate.android.sdk.demoapp.demos.getbalance

import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.View
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import cash.z.ecc.android.bip39.Mnemonics
import cash.z.ecc.android.bip39.toSeed
import pirate.android.sdk.Synchronizer
import pirate.android.sdk.block.CompactBlockProcessor
import pirate.android.sdk.demoapp.BaseDemoFragment
import pirate.android.sdk.demoapp.R
import pirate.android.sdk.demoapp.databinding.FragmentGetBalanceBinding
import pirate.android.sdk.demoapp.ext.requireApplicationContext
import pirate.android.sdk.demoapp.util.SyncBlockchainBenchmarkTrace
import pirate.android.sdk.demoapp.util.fromResources
import pirate.android.sdk.ext.PirateSdk
import pirate.android.sdk.ext.convertArrrtoshiToArrrString
import pirate.android.sdk.internal.Twig
import pirate.android.sdk.model.Account
import pirate.android.sdk.model.PercentDecimal
import pirate.android.sdk.model.WalletBalance
import pirate.android.sdk.model.Arrrtoshi
import pirate.android.sdk.model.PirateNetwork
import pirate.android.sdk.tool.DerivationTool
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch

/**
 * Displays the available balance && total balance associated with the seed defined by the default config.
 * comments.
 */
@Suppress("TooManyFunctions")
class GetBalanceFragment : BaseDemoFragment<FragmentGetBalanceBinding>() {

    override fun inflateBinding(layoutInflater: LayoutInflater): FragmentGetBalanceBinding =
        FragmentGetBalanceBinding.inflate(layoutInflater)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        reportTraceEvent(SyncBlockchainBenchmarkTrace.Event.BALANCE_SCREEN_START)
        setHasOptionsMenu(true)
    }

    override fun onPrepareOptionsMenu(menu: Menu) {
        super.onPrepareOptionsMenu(menu)
        // We rather hide options menu actions while actively using the Synchronizer
        menu.setGroupVisible(R.id.main_menu_group, false)
    }

    override fun onDestroy() {
        super.onDestroy()
        reportTraceEvent(SyncBlockchainBenchmarkTrace.Event.BALANCE_SCREEN_END)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val seedPhrase = sharedViewModel.seedPhrase.value
        val seed = Mnemonics.MnemonicCode(seedPhrase).toSeed()
        val network = PirateNetwork.fromResources(requireApplicationContext())

        binding.shield.apply {
            setOnClickListener {
                lifecycleScope.launch {
                    sharedViewModel.synchronizerFlow.value?.shieldFunds(
                        DerivationTool.getInstance().deriveUnifiedSpendingKey(
                            seed,
                            network,
                            Account.DEFAULT
                        )
                    )
                }
            }
        }

        monitorChanges()
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
                        .collect { onSaplingBalance(it) }
                }
                launch {
                    sharedViewModel.synchronizerFlow
                        .filterNotNull()
                        .flatMapLatest { it.orchardBalances }
                        .collect { onOrchardBalance(it) }
                }
                launch {
                    sharedViewModel.synchronizerFlow
                        .filterNotNull()
                        .flatMapLatest { it.transparentBalances }
                        .collect { onTransparentBalance(it) }
                }
            }
        }
    }

    private fun onOrchardBalance(
        orchardBalance: WalletBalance?
    ) {
        binding.orchardBalance.apply {
            text = orchardBalance.humanString()
        }
    }

    private fun onSaplingBalance(
        saplingBalance: WalletBalance?
    ) {
        binding.saplingBalance.apply {
            text = saplingBalance.humanString()
        }
    }

    private fun onTransparentBalance(
        transparentBalance: WalletBalance?
    ) {
        binding.transparentBalance.apply {
            text = transparentBalance.humanString()
        }

        binding.shield.apply {
            // TODO [#776]: Support variable fees
            // TODO [#776]: https://github.com/zcash/zcash-android-wallet-sdk/issues/776
            visibility = if ((transparentBalance?.available ?: Arrrtoshi(0)) > PirateSdk.MINERS_FEE) {
                View.VISIBLE
            } else {
                View.GONE
            }
        }
    }

    private fun onStatus(status: Synchronizer.Status) {
        Twig.debug { "Synchronizer status: $status" }
        // report benchmark event
        val traceEvents = when (status) {
            Synchronizer.Status.SYNCING -> {
                SyncBlockchainBenchmarkTrace.Event.BLOCKCHAIN_SYNC_START
            }
            Synchronizer.Status.SYNCED -> {
                SyncBlockchainBenchmarkTrace.Event.BLOCKCHAIN_SYNC_END
            }
            else -> null
        }
        traceEvents?.let { reportTraceEvent(it) }

        binding.textStatus.text = "Status: $status"
        sharedViewModel.synchronizerFlow.value?.let { synchronizer ->
            onOrchardBalance(synchronizer.orchardBalances.value)
            onSaplingBalance(synchronizer.saplingBalances.value)
            onTransparentBalance(synchronizer.transparentBalances.value)
        }
    }

    @Suppress("MagicNumber")
    private fun onProgress(percent: PercentDecimal) {
        if (percent.isLessThanHundredPercent()) {
            binding.textStatus.text = "Syncing blocks...${percent.toPercentage()}%"
        }
    }

    private fun onProcessorInfoUpdated(info: CompactBlockProcessor.ProcessorInfo) {
        if (info.isSyncing) binding.textStatus.text = "Syncing blocks...${info.syncProgress}%"
    }
}

@Suppress("MagicNumber")
private fun WalletBalance?.humanString() = if (null == this) {
    "Calculating balance"
} else {
    """
                Pending balance: ${pending.convertArrrtoshiToArrrString(12)}
                Available balance: ${available.convertArrrtoshiToArrrString(12)}
                Total balance: ${total.convertArrrtoshiToArrrString(12)}
    """.trimIndent()
}
