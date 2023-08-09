package pirate.fixture

import pirate.android.sdk.internal.db.DatabaseCoordinator
import pirate.android.sdk.model.PirateNetwork

/**
 * Provides a unified way for getting a fixture root name for database cache files for test purposes.
 */
object DatabaseCacheFilesRootFixture {
    const val TEST_CACHE_ROOT_NAME = DatabaseCoordinator.DB_FS_BLOCK_DB_ROOT_NAME
    const val TEST_CACHE_ROOT_NAME_ALIAS = "zcash_sdk"
    val TEST_NETWORK = PirateNetwork.Testnet

    internal fun newCacheRoot(
        name: String = TEST_CACHE_ROOT_NAME,
        alias: String = TEST_CACHE_ROOT_NAME_ALIAS,
        network: String = TEST_NETWORK.networkName
    ) = "${alias}_${network}_$name"
}
