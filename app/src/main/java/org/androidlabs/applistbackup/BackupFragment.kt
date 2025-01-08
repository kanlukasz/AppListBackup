package org.androidlabs.applistbackup

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.androidlabs.applistbackup.reader.BackupReaderActivity
import org.androidlabs.applistbackup.ui.LoadingView
import org.androidlabs.applistbackup.ui.theme.AppListBackupTheme

class BackupFragment : Fragment() {
    private val viewModel: BackupViewModel by viewModels()
    private lateinit var openFolderLauncher: ActivityResultLauncher<Intent>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        openFolderLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            Log.d("SettingsFragment", "Open folder result: $result")
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setContent {
                AppListBackupTheme {
                    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                        ActivityState(
                            modifier = Modifier.padding(innerPadding),
                            viewModel = viewModel,
                            openLastBackup = ::openLastBackup,
                            runBackup = ::runBackup,
                        )
                    }
                }
            }
        }
    }

    private fun openLastBackup() {
        val context = requireContext()
        lifecycleScope.launch {
            viewModel.setLoading(true)
            val lastBackupUri = withContext(Dispatchers.IO) {
                BackupService.getLastCreatedFileUri(context)
            }

            val intent = withContext(Dispatchers.Default) {
                Intent(context, BackupReaderActivity::class.java).apply {
                    if (lastBackupUri != null) {
                        putExtra("uri", lastBackupUri.toString())
                    }
                }
            }
            startActivity(intent)
            viewModel.setLoading(false)
        }
    }

    private fun runBackup() {
        BackupService.run(requireContext())
    }
}

@Composable
private fun ActivityState(
    modifier: Modifier = Modifier,
    viewModel: BackupViewModel,
    openLastBackup: () -> Unit,
    runBackup: () -> Unit,
) {
    val isNotificationEnabled = viewModel.notificationEnabled.observeAsState(initial = false)
    val backupUri = viewModel.backupUri.observeAsState()
    val isLoading = viewModel.isLoading.observeAsState(initial = false)

    LaunchedEffect(key1 = true) {
        viewModel.refreshNotificationStatus()
        viewModel.refreshBackupUri()
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshNotificationStatus()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val context = LocalContext.current
    val settingsIntent = remember {
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.align(Alignment.Center)
        ) {
            if (isNotificationEnabled.value != true) {
                Text(
                    text = stringResource(R.string.notifications_disabled),
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(8.dp))
                Button(onClick = { context.startActivity(settingsIntent) }) {
                    Text(text = stringResource(R.string.notifications_enable))
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (backupUri.value != null) {
                Button(onClick = runBackup) {
                    Text(text = stringResource(R.string.run_backup))
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(onClick = openLastBackup, enabled = !isLoading.value) {
                    if (isLoading.value) {
                        LoadingView()
                    } else {
                        Text(text = stringResource(R.string.view_backup))
                    }
                }
            } else {
                Text(
                    text = stringResource(R.string.destination_not_set),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
