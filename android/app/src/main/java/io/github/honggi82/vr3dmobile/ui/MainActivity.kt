package io.github.honggi82.vr3dmobile.ui

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import io.github.honggi82.vr3dmobile.R
import io.github.honggi82.vr3dmobile.packageio.Vr3dPackageImporter
import io.github.honggi82.vr3dmobile.storage.ProjectRepository
import io.github.honggi82.vr3dmobile.storage.ProjectSummary
import io.github.honggi82.vr3dmobile.ui.Ui.asPill
import io.github.honggi82.vr3dmobile.ui.Ui.dp
import java.nio.file.Files
import java.nio.file.StandardOpenOption
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID
import java.util.concurrent.Executors

class MainActivity : Activity() {
    private lateinit var repository: ProjectRepository
    private lateinit var projectList: LinearLayout
    private lateinit var progress: ProgressBar
    private val worker = Executors.newSingleThreadExecutor()

    override fun attachBaseContext(newBase: Context) = super.attachBaseContext(LanguageStore.wrap(newBase))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repository = ProjectRepository(this)
        buildUi()
        refreshLibrary()
        consumeViewIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        consumeViewIntent(intent)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == OPEN_PACKAGE && resultCode == RESULT_OK) data?.data?.let(::importUri)
    }

    override fun onDestroy() {
        worker.shutdownNow()
        super.onDestroy()
    }

    private fun buildUi() {
        window.statusBarColor = Ui.BACKGROUND
        window.navigationBarColor = Ui.BACKGROUND
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(18), dp(20), dp(18))
            setBackgroundColor(Ui.BACKGROUND)
        }
        val heading = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        heading.addView(TextView(this).apply {
            text = getString(R.string.library)
            textSize = 29f
            setTextColor(Ui.TEXT)
            setTypeface(typeface, Typeface.BOLD)
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        heading.addView(Button(this).apply {
            text = getString(R.string.language_switch)
            asPill()
            setOnClickListener {
                LanguageStore.toggle(this@MainActivity)
                recreate()
            }
        })
        root.addView(heading)

        projectList = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(16), 0, dp(16))
        }
        root.addView(ScrollView(this).apply { addView(projectList) }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))

        progress = ProgressBar(this).apply { visibility = View.GONE }
        root.addView(progress, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(4)))
        root.addView(Button(this).apply {
            text = getString(R.string.import_package)
            asPill(Ui.ACCENT, Ui.BACKGROUND)
            setTypeface(typeface, Typeface.BOLD)
            setOnClickListener { openDocumentPicker() }
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        setContentView(root)
    }

    private fun refreshLibrary() {
        projectList.removeAllViews()
        val projects = repository.list()
        if (projects.isEmpty()) {
            projectList.addView(TextView(this).apply {
                text = getString(R.string.library_empty)
                textSize = 16f
                gravity = Gravity.CENTER
                setTextColor(Ui.MUTED)
                setPadding(0, dp(80), 0, dp(80))
            })
        } else {
            projects.forEach { projectList.addView(projectCard(it)) }
        }
    }

    private fun projectCard(project: ProjectSummary): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(16), dp(18), dp(16))
            background = Ui.rounded(Ui.PANEL, dp(18), Ui.LINE)
        }
        card.addView(TextView(this).apply {
            text = getString(R.string.project_title, project.packageId.take(8))
            textSize = 20f
            setTextColor(Ui.TEXT)
            setTypeface(typeface, Typeface.BOLD)
        })
        val date = DATE_FORMAT.format(project.createdAt.atZone(ZoneId.systemDefault()))
        card.addView(TextView(this).apply {
            text = getString(R.string.project_details, date, project.modelVariant.uppercase())
            textSize = 13f
            setTextColor(Ui.MUTED)
            setPadding(0, dp(5), 0, dp(12))
        })
        card.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(Button(this@MainActivity).apply {
                text = getString(R.string.open_project)
                asPill(Ui.ACCENT, Ui.BACKGROUND)
                setOnClickListener { openViewer(project.packageId) }
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(Button(this@MainActivity).apply {
                text = getString(R.string.delete_project)
                asPill()
                setOnClickListener { confirmDelete(project.packageId) }
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        })
        return card.apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).also {
                it.bottomMargin = dp(12)
            }
        }
    }

    private fun openDocumentPicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/octet-stream"
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("application/vnd.vr3d", "application/zip", "application/octet-stream"))
        }
        startActivityForResult(intent, OPEN_PACKAGE)
    }

    private fun consumeViewIntent(sourceIntent: Intent) {
        if (sourceIntent.action != Intent.ACTION_VIEW) return
        val uri = sourceIntent.data ?: return
        setIntent(Intent(Intent.ACTION_MAIN))
        importUri(uri)
    }

    private fun importUri(uri: Uri) {
        val name = displayName(uri)
        if (name == null || !name.lowercase().endsWith(".vr3d")) {
            showFailure("The selected file must use the .vr3d extension")
            return
        }
        progress.visibility = View.VISIBLE
        worker.execute {
            val temporary = cacheDir.toPath().resolve("incoming-${UUID.randomUUID()}.vr3d")
            try {
                copyUriWithLimit(uri, temporary)
                val imported = Vr3dPackageImporter.importPackage(temporary, repository.root)
                runOnUiThread {
                    if (isDestroyed) return@runOnUiThread
                    progress.visibility = View.GONE
                    Toast.makeText(this, R.string.import_complete, Toast.LENGTH_SHORT).show()
                    refreshLibrary()
                    openViewer(imported.manifest.packageId)
                }
            } catch (error: Exception) {
                runOnUiThread {
                    if (isDestroyed) return@runOnUiThread
                    progress.visibility = View.GONE
                    showFailure(error.message ?: error.javaClass.simpleName)
                }
            } finally {
                Files.deleteIfExists(temporary)
            }
        }
    }

    private fun copyUriWithLimit(uri: Uri, target: java.nio.file.Path) {
        val input = contentResolver.openInputStream(uri) ?: throw IllegalArgumentException("The selected file cannot be opened")
        input.use {
            Files.newOutputStream(target, StandardOpenOption.CREATE_NEW).use { output ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                var total = 0L
                while (true) {
                    val read = it.read(buffer)
                    if (read < 0) break
                    total += read
                    if (total > Vr3dPackageImporter.MAX_PACKAGE_BYTES) throw IllegalArgumentException("The package is larger than 512 MiB")
                    output.write(buffer, 0, read)
                }
            }
        }
    }

    private fun displayName(uri: Uri): String? {
        if (uri.scheme == "file") return uri.lastPathSegment
        return try {
            contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun openViewer(packageId: String) {
        startActivity(Intent(this, ViewerActivity::class.java).putExtra(ViewerActivity.EXTRA_PACKAGE_ID, packageId))
    }

    private fun confirmDelete(packageId: String) {
        AlertDialog.Builder(this)
            .setTitle(R.string.delete_title)
            .setMessage(R.string.delete_message)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.delete_project) { _, _ ->
                repository.delete(packageId)
                refreshLibrary()
            }
            .show()
    }

    private fun showFailure(message: String) {
        Toast.makeText(this, getString(R.string.import_failed, message), Toast.LENGTH_LONG).show()
    }

    companion object {
        private const val OPEN_PACKAGE = 41
        private val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
    }
}
