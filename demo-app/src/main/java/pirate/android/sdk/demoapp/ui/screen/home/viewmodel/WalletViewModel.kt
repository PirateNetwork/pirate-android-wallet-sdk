package pirate.android.sdk.demoapp.ui.screen.home.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import cash.z.ecc.android.bip39.Mnemonics
import cash.z.ecc.android.bip39.toSeed
import pirate.android.sdk.PirateSynchronizer
import pirate.android.sdk.block.PirateCompactBlockProcessor
import pirate.android.sdk.demoapp.WalletCoordinator
import pirate.android.sdk.demoapp.getInstance
import pirate.android.sdk.demoapp.model.PercentDecimal
import pirate.android.sdk.demoapp.model.PersistableWallet
import pirate.android.sdk.demoapp.model.WalletAddresses
import pirate.android.sdk.demoapp.model.ZecSend
import pirate.android.sdk.demoapp.model.send
import pirate.android.sdk.demoapp.preference.EncryptedPreferenceKeys
import pirate.android.sdk.demoapp.preference.EncryptedPreferenceSingleton
import pirate.android.sdk.demoapp.ui.common.ANDROID_STATE_FLOW_TIMEOUT
import pirate.android.sdk.demoapp.ui.common.throttle
import pirate.android.sdk.demoapp.util.Twig
import pirate.android.sdk.model.Account
import pirate.android.sdk.model.BlockHeight
import pirate.android.sdk.model.PendingTransaction
import pirate.android.sdk.model.PirateWalletBalance
import pirate.android.sdk.model.Arrrtoshi
import pirate.android.sdk.model.isMined
import pirate.android.sdk.model.isSubmitSuccess
import pirate.android.sdk.tool.PirateDerivationTool
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.WhileSubscribed
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime

// To make this more multiplatform compatible, we need to remove the dependency on Context
// for loading the preferences.
class WalletViewModel(application: Application) : AndroidViewModel(application) {
    private val walletCoordinator = WalletCoordinator.getInstance(application)

    /*
     * Using the Mutex may be overkill, but it ensures that if multiple calls are accidentally made
     * that they have a consistent ordering.
     */
    private val persistWalletMutex = Mutex()

    /**
     * PirateSynchronizer that is retained long enough to survive configuration changes.
     */
    val synchronizer = walletCoordinator.synchronizer.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(ANDROID_STATE_FLOW_TIMEOUT),
        null
    )

    val secretState: StateFlow<SecretState> = walletCoordinator.persistableWallet
        .map { persistableWallet ->
            if (null == persistableWallet) {
                SecretState.None
            } else {
                SecretState.Ready(persistableWallet)
            }
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(ANDROID_STATE_FLOW_TIMEOUT),
            SecretState.Loading
        )

    val spendingKey = secretState
        .filterIsInstance<SecretState.Ready>()
        .map { it.persistableWallet }
        .map {
            val bip39Seed = withContext(Dispatchers.IO) {
                Mnemonics.MnemonicCode(it.seedPhrase.joinToString()).toSeed()
            }
            PirateDerivationTool.derivePirateUnifiedSpendingKey(
                seed = bip39Seed,
                network = it.network,
                account = Account.DEFAULT
            )
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(ANDROID_STATE_FLOW_TIMEOUT),
            null
        )

    @OptIn(ExperimentalCoroutinesApi::class, ExperimentalTime::class)
    val walletSnapshot: StateFlow<WalletSnapshot?> = synchronizer
        .flatMapLatest {
            if (null == it) {
                flowOf(null)
            } else {
                it.toWalletSnapshot()
            }
        }
        .throttle(1.seconds)
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(ANDROID_STATE_FLOW_TIMEOUT),
            null
        )

    val addresses: StateFlow<WalletAddresses?> = synchronizer
        .filterNotNull()
        .map {
            WalletAddresses.new(it)
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(ANDROID_STATE_FLOW_TIMEOUT),
            null
        )

    /**
     * Creates a wallet asynchronously and then persists it.  Clients observe
     * [secretState] to see the side effects.  This would be used for a user creating a new wallet.
     */
    /*
     * Although waiting for the wallet to be written and then read back is slower, it is probably
     * safer because it 1. guarantees the wallet is written to disk and 2. has a single source of truth.
     */
    fun persistNewWallet() {
        val application = getApplication<Application>()

        viewModelScope.launch {
            val newWallet = PersistableWallet.new(application)
            persistExistingWallet(newWallet)
        }
    }

    /**
     * Persists a wallet asynchronously.  Clients observe [secretState]
     * to see the side effects.  This would be used for a user restoring a wallet from a backup.
     */
    fun persistExistingWallet(persistableWallet: PersistableWallet) {
        val application = getApplication<Application>()

        viewModelScope.launch {
            val preferenceProvider = EncryptedPreferenceSingleton.getInstance(application)
            persistWalletMutex.withLock {
                EncryptedPreferenceKeys.PERSISTABLE_WALLET.putValue(preferenceProvider, persistableWallet)
            }
        }
    }

    /**
     * Asynchronously sends funds.
     */
    fun send(zecSend: ZecSend) {
        // Note that if synchronizer is null this will silently fail
        val synchronizer = synchronizer.value
        if (null != synchronizer) {
            viewModelScope.launch {
                val spendingKey = spendingKey.filterNotNull().first()
                synchronizer.send(spendingKey, zecSend)
            }
        } else {
            Twig.info { "Unable to send funds" }
        }
    }

    /**
     * Asynchronously shields transparent funds.
     */
    fun shieldFunds() {
        // Note that if synchronizer is null this will silently fail
        val synchronizer = synchronizer.value

        if (null != synchronizer) {
            viewModelScope.launch {
                val spendingKey = spendingKey.filterNotNull().first()
                synchronizer.shieldFunds(spendingKey)
            }
        } else {
            Twig.info { "Unable to shield funds" }
        }
    }

    /**
     * This method only has an effect if the synchronizer currently is loaded.
     */
    fun rescanBlockchain() {
        viewModelScope.launch {
            walletCoordinator.rescanBlockchain()
        }
    }

    /**
     * This asynchronously resets the SDK state.  This is non-destructive, as SDK state can be rederived.
     *
     * This could be used as a troubleshooting step in debugging.
     */
    fun resetSdk() {
        walletCoordinator.resetSdk()
    }
}

/**
 * Represents the state of the wallet secret.
 */
sealed class SecretState {
    object Loading : SecretState()
    object None : SecretState()
    class Ready(val persistableWallet: PersistableWallet) : SecretState()
}

/**
 * Represents all kind of PirateSynchronizer errors
 */
// TODO [#529] https://github.com/zcash/secant-android-wallet/issues/529
sealed class PirateSynchronizerError {
    abstract fun getCauseMessage(): String?

    class Critical(val error: Throwable?) : PirateSynchronizerError() {
        override fun getCauseMessage(): String? = error?.localizedMessage
    }

    class Processor(val error: Throwable?) : PirateSynchronizerError() {
        override fun getCauseMessage(): String? = error?.localizedMessage
    }

    class Submission(val error: Throwable?) : PirateSynchronizerError() {
        override fun getCauseMessage(): String? = error?.localizedMessage
    }

    class Setup(val error: Throwable?) : PirateSynchronizerError() {
        override fun getCauseMessage(): String? = error?.localizedMessage
    }

    class Chain(val x: BlockHeight, val y: BlockHeight) : PirateSynchronizerError() {
        override fun getCauseMessage(): String = "$x, $y"
    }
}

private fun PirateSynchronizer.toCommonError(): Flow<PirateSynchronizerError?> = callbackFlow {
    // just for initial default value emit
    trySend(null)

    onCriticalErrorHandler = {
        Twig.error { "WALLET - Error Critical: $it" }
        trySend(PirateSynchronizerError.Critical(it))
        false
    }
    onProcessorErrorHandler = {
        Twig.error { "WALLET - Error Processor: $it" }
        trySend(PirateSynchronizerError.Processor(it))
        false
    }
    onSubmissionErrorHandler = {
        Twig.error { "WALLET - Error Submission: $it" }
        trySend(PirateSynchronizerError.Submission(it))
        false
    }
    onSetupErrorHandler = {
        Twig.error { "WALLET - Error Setup: $it" }
        trySend(PirateSynchronizerError.Setup(it))
        false
    }
    onChainErrorHandler = { x, y ->
        Twig.error { "WALLET - Error Chain: $x, $y" }
        trySend(PirateSynchronizerError.Chain(x, y))
    }

    awaitClose {
        // nothing to close here
    }
}

// No good way around needing magic numbers for the indices
@Suppress("MagicNumber")
private fun PirateSynchronizer.toWalletSnapshot() =
    combine(
        status, // 0
        processorInfo, // 1
        orchardBalances, // 2
        saplingBalances, // 3
        transparentBalances, // 4
        pendingTransactions.distinctUntilChanged(), // 5
        progress, // 6
        toCommonError() // 7
    ) { flows ->
        val pendingCount = (flows[5] as List<*>)
            .filterIsInstance(PendingTransaction::class.java)
            .count {
                it.isSubmitSuccess() && !it.isMined()
            }
        val orchardBalance = flows[2] as PirateWalletBalance?
        val saplingBalance = flows[3] as PirateWalletBalance?
        val transparentBalance = flows[4] as PirateWalletBalance?

        val progressPercentDecimal = (flows[6] as Int).let { value ->
            if (value > PercentDecimal.MAX || value < PercentDecimal.MIN) {
                PercentDecimal.ZERO_PERCENT
            }
            PercentDecimal((flows[6] as Int) / 100f)
        }

        WalletSnapshot(
            flows[0] as PirateSynchronizer.PirateStatus,
            flows[1] as PirateCompactBlockProcessor.ProcessorInfo,
            orchardBalance ?: PirateWalletBalance(Arrrtoshi(0), Arrrtoshi(0)),
            saplingBalance ?: PirateWalletBalance(Arrrtoshi(0), Arrrtoshi(0)),
            transparentBalance ?: PirateWalletBalance(Arrrtoshi(0), Arrrtoshi(0)),
            pendingCount,
            progressPercentDecimal,
            flows[7] as PirateSynchronizerError?
        )
    }