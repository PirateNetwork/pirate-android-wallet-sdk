package pirate.android.sdk.internal.db.derived

import pirate.android.sdk.internal.model.DbTransactionOverview
import pirate.android.sdk.internal.model.EncodedTransaction
import pirate.android.sdk.internal.repository.DerivedDataRepository
import pirate.android.sdk.model.BlockHeight
import pirate.android.sdk.model.TransactionRecipient
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import java.util.UUID

@Suppress("TooManyFunctions")
internal class DbDerivedDataRepository(
    private val derivedDataDb: DerivedDataDb
) : DerivedDataRepository {
    private val invalidatingFlow = MutableStateFlow(UUID.randomUUID())

    override suspend fun lastScannedHeight(): BlockHeight {
        return derivedDataDb.blockTable.lastScannedHeight()
    }

    override suspend fun firstUnenhancedHeight(): BlockHeight? {
        return derivedDataDb.allTransactionView.firstUnenhancedHeight()
    }

    override suspend fun firstScannedHeight(): BlockHeight {
        return derivedDataDb.blockTable.firstScannedHeight()
    }

    override suspend fun isInitialized(): Boolean {
        return derivedDataDb.blockTable.count() > 0
    }

    override suspend fun findEncodedTransactionById(txId: Long): EncodedTransaction? {
        return derivedDataDb.transactionTable.findEncodedTransactionById(txId)
    }

    override suspend fun findNewTransactions(blockHeightRange: ClosedRange<BlockHeight>): List<DbTransactionOverview> =
        derivedDataDb.allTransactionView.getTransactionRange(blockHeightRange).toList()

    override suspend fun getOldestTransaction() = derivedDataDb.allTransactionView.getOldestTransaction()

    override suspend fun findMinedHeight(rawTransactionId: ByteArray) = derivedDataDb.transactionTable
        .findMinedHeight(rawTransactionId)

    override suspend fun findMatchingTransactionId(rawTransactionId: ByteArray) = derivedDataDb.transactionTable
        .findDatabaseId(rawTransactionId)

    override suspend fun findBlockHash(height: BlockHeight) = derivedDataDb.blockTable.findBlockHash(height)

    override suspend fun getTransactionCount() = derivedDataDb.transactionTable.count()

    override fun invalidate() {
        invalidatingFlow.value = UUID.randomUUID()
    }

    override suspend fun getAccountCount() = derivedDataDb.accountTable.count()
        // toInt() should be safe because we expect very few accounts
        .toInt()

    override val allTransactions: Flow<List<DbTransactionOverview>>
        get() = invalidatingFlow.map { derivedDataDb.allTransactionView.getAllTransactions().toList() }

    override fun getNoteIds(transactionId: Long) = derivedDataDb.txOutputsView.getNoteIds(transactionId)

    override fun getRecipients(transactionId: Long): Flow<TransactionRecipient> {
        return derivedDataDb.txOutputsView.getRecipients(transactionId)
    }

    override suspend fun close() {
        derivedDataDb.close()
    }
}
