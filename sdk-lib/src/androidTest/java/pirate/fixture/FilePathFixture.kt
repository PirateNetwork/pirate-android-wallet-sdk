package pirate.fixture

import pirate.android.sdk.internal.storage.block.FileCompactBlockRepository
import java.io.File

object FilePathFixture {
    private val DEFAULT_ROOT_DIR_PATH = DatabasePathFixture.new()
    internal const val DEFAULT_BLOCKS_DIR_NAME = FileCompactBlockRepository.BLOCKS_DOWNLOAD_DIRECTORY

    internal fun newBlocksDir(
        rootDirectoryPath: String = DEFAULT_ROOT_DIR_PATH,
        blockDirectoryName: String = DEFAULT_BLOCKS_DIR_NAME
    ) = File(rootDirectoryPath, blockDirectoryName)
}
