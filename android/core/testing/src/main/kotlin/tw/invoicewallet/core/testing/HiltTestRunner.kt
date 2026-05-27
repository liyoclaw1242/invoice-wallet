package tw.invoicewallet.core.testing

import android.app.Application
import android.content.Context
import androidx.test.runner.AndroidJUnitRunner

/**
 * Instrumentation runner that swaps in Hilt's test Application so `@HiltAndroidTest`
 * works. Reference by name from a module's `testInstrumentationRunner`:
 * `tw.invoicewallet.core.testing.HiltTestRunner`.
 *
 * HiltTestApplication is referenced by its FQN string because it is generated in the
 * *consuming* module's androidTest compilation, not here.
 */
class HiltTestRunner : AndroidJUnitRunner() {
    override fun newApplication(
        cl: ClassLoader?,
        className: String?,
        context: Context?,
    ): Application = super.newApplication(
        cl,
        "dagger.hilt.android.testing.HiltTestApplication",
        context,
    )
}
