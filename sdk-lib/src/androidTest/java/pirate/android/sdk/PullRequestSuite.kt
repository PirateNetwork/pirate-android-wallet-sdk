package pirate.android.sdk

import pirate.android.sdk.integration.SanityTest
import pirate.android.sdk.integration.SmokeTest
import pirate.android.sdk.integration.service.ChangeServiceTest
import pirate.android.sdk.internal.transaction.PiratePersistentTransactionManagerTest
import pirate.android.sdk.jni.BranchIdTest
import pirate.android.sdk.jni.TransparentTest
import org.junit.runner.RunWith
import org.junit.runners.Suite

/**
 * Suite of tests to run before submitting a pull request.
 *
 * For now, these are just the tests that are known to be recently updated and that pass. In the
 * near future this suite will contain only fast running tests that can be used to quickly validate
 * that a PR hasn't broken anything major.
 */
@RunWith(Suite::class)
@Suite.SuiteClasses(
    // Fast tests that only run locally and don't require darksidewalletd or lightwalletd
    BranchIdTest::class,
    TransparentTest::class,
    PiratePersistentTransactionManagerTest::class,

    // potentially exclude because these are long-running (and hit external srvcs)
    SanityTest::class,

    // potentially exclude because these hit external services
    ChangeServiceTest::class,
    SmokeTest::class,
)
class PullRequestSuite
