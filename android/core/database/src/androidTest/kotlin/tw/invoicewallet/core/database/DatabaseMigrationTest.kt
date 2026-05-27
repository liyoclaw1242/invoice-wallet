package tw.invoicewallet.core.database

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Validates the database schema is creatable from the exported schema JSON.
 * Starts at v1 (no migrations yet); future migrations get a test per step here.
 */
@RunWith(AndroidJUnit4::class)
class DatabaseMigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        InvoiceWalletDatabase::class.java,
    )

    @Test
    fun creates_the_v1_schema_from_the_exported_schema() {
        helper.createDatabase(TEST_DB, 1).close()
    }

    private companion object {
        const val TEST_DB = "migration-test.db"
    }
}
