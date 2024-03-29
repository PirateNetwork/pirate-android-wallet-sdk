package pirate.fixture

import pirate.android.sdk.internal.db.DatabaseCoordinator
import pirate.android.sdk.model.PirateNetwork

/**
 * Provides a unified way for getting a fixture database files names for test purposes.
 */
object DatabaseNameFixture {
    const val TEST_DB_NAME = "empty.db"
    const val TEST_DB_JOURNAL_NAME_SUFFIX = DatabaseCoordinator.DATABASE_FILE_JOURNAL_SUFFIX
    const val TEST_DB_WAL_NAME_SUFFIX = DatabaseCoordinator.DATABASE_FILE_WAL_SUFFIX

    const val TEST_DB_ALIAS = "zcash_sdk"
    val TEST_DB_NETWORK = PirateNetwork.Testnet

    internal fun newDb(
        name: String = TEST_DB_NAME,
        alias: String = TEST_DB_ALIAS,
        network: String = TEST_DB_NETWORK.networkName
    ) = "${alias}_${network}_$name"

    internal fun newDbJournal(
        name: String = TEST_DB_NAME,
        alias: String = TEST_DB_ALIAS,
        network: String = TEST_DB_NETWORK.networkName
    ) = "${alias}_${network}_$name-$TEST_DB_JOURNAL_NAME_SUFFIX"

    internal fun newDbWal(
        name: String = TEST_DB_NAME,
        alias: String = TEST_DB_ALIAS,
        network: String = TEST_DB_NETWORK.networkName
    ) = "${alias}_${network}_$name-$TEST_DB_WAL_NAME_SUFFIX"
}
