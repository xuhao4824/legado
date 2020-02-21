package io.legado.app.ui.config

import android.app.Activity.RESULT_OK
import android.content.ComponentName
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import io.legado.app.App
import io.legado.app.R
import io.legado.app.constant.EventBus
import io.legado.app.constant.PreferKey
import io.legado.app.help.AppConfig
import io.legado.app.help.BookHelp
import io.legado.app.help.permission.Permissions
import io.legado.app.help.permission.PermissionsCompat
import io.legado.app.lib.dialogs.alert
import io.legado.app.lib.theme.ATH
import io.legado.app.receiver.SharedReceiverActivity
import io.legado.app.ui.filechooser.FileChooserDialog
import io.legado.app.ui.widget.number.NumberPickerDialog
import io.legado.app.utils.*


class OtherConfigFragment : PreferenceFragmentCompat(),
    FileChooserDialog.CallBack,
    SharedPreferences.OnSharedPreferenceChangeListener {

    private val requestCodeDownloadPath = 25324
    private val packageManager = App.INSTANCE.packageManager
    private val componentName = ComponentName(
        App.INSTANCE,
        SharedReceiverActivity::class.java.name
    )

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        putPrefBoolean(PreferKey.processText, isProcessTextEnabled())
        addPreferencesFromResource(R.xml.pref_config_other)
        upPreferenceSummary(PreferKey.downloadPath, BookHelp.downloadPath)
        upPreferenceSummary(PreferKey.threadCount, AppConfig.threadCount.toString())
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        preferenceManager.sharedPreferences.registerOnSharedPreferenceChangeListener(this)
        ATH.applyEdgeEffectColor(listView)
    }

    override fun onDestroy() {
        super.onDestroy()
        preferenceManager.sharedPreferences.unregisterOnSharedPreferenceChangeListener(this)
    }

    override fun onPreferenceTreeClick(preference: Preference?): Boolean {
        when (preference?.key) {
            PreferKey.threadCount -> NumberPickerDialog(requireContext())
                .setTitle(getString(R.string.threads_num_title))
                .setMaxValue(999)
                .setMinValue(1)
                .setValue(AppConfig.threadCount)
                .show {
                    AppConfig.threadCount = it
                }
            PreferKey.downloadPath -> selectDownloadPath()
            PreferKey.cleanCache -> {
                BookHelp.clearCache()
                toast(R.string.clear_cache_success)
            }
        }
        return super.onPreferenceTreeClick(preference)
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        when (key) {
            PreferKey.downloadPath -> {
                upPreferenceSummary(key, BookHelp.downloadPath)
            }
            PreferKey.threadCount -> upPreferenceSummary(
                PreferKey.threadCount,
                AppConfig.threadCount.toString()
            )
            PreferKey.recordLog -> LogUtils.upLevel()
            PreferKey.processText -> sharedPreferences?.let {
                setProcessTextEnable(it.getBoolean(key, true))
            }
            PreferKey.showRss -> postEvent(EventBus.SHOW_RSS, "unused")
        }
    }

    private fun upPreferenceSummary(preferenceKey: String, value: String?) {
        val preference = findPreference<Preference>(preferenceKey) ?: return
        when (preferenceKey) {
            PreferKey.threadCount -> preference.summary = getString(R.string.threads_num, value)
            else -> if (preference is ListPreference) {
                val index = preference.findIndexOfValue(value)
                // Set the summary to reflect the new value.
                preference.summary = if (index >= 0) preference.entries[index] else null
            } else {
                preference.summary = value
            }
        }
    }

    private fun isProcessTextEnabled(): Boolean {
        return packageManager.getComponentEnabledSetting(componentName) != PackageManager.COMPONENT_ENABLED_STATE_DISABLED
    }

    private fun setProcessTextEnable(enable: Boolean) {
        if (enable) {
            packageManager.setComponentEnabledSetting(
                componentName,
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP
            )
        } else {
            packageManager.setComponentEnabledSetting(
                componentName,
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP
            )
        }
    }

    private fun selectDownloadPath() {
        alert {
            titleResource = R.string.select_folder
            items(resources.getStringArray(R.array.select_folder).toList()) { _, i ->
                when (i) {
                    0 -> {
                        removePref(PreferKey.downloadPath)
                    }
                    1 -> {
                        try {
                            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
                            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            startActivityForResult(intent, requestCodeDownloadPath)
                        } catch (e: Exception) {
                            e.printStackTrace()
                            toast(e.localizedMessage ?: "ERROR")
                        }
                    }
                    2 -> PermissionsCompat.Builder(this@OtherConfigFragment)
                        .addPermissions(*Permissions.Group.STORAGE)
                        .rationale(R.string.tip_perm_request_storage)
                        .onGranted {
                            FileChooserDialog.show(
                                childFragmentManager,
                                requestCodeDownloadPath,
                                mode = FileChooserDialog.DIRECTORY,
                                initPath = BookHelp.downloadPath
                            )
                        }
                        .request()
                }
            }
        }.show()
    }

    override fun onFilePicked(requestCode: Int, currentPath: String) {
        if (requestCode == requestCodeDownloadPath) {
            putPrefString(PreferKey.downloadPath, currentPath)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            requestCodeDownloadPath -> if (resultCode == RESULT_OK) {
                data?.data?.let { uri ->
                    requireContext().contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    )
                    putPrefString(PreferKey.downloadPath, uri.toString())
                }
            }
        }
    }
}