package pirate.android.sdk.darkside.test

open class DarksideTest(name: String = javaClass.simpleName) : ScopedTest() {
    val sithLord = DarksideTestCoordinator()
    val validator = sithLord.validator

    fun runOnce(block: () -> Unit) {
        if (!ranOnce) {
            sithLord.enterTheDarkside()
            sithLord.synchronizer.start(classScope)
            block()
            ranOnce = true
        }
    }
    companion object {
        private var ranOnce = false
    }
}
