import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.activity_mainxml.ui.theme.copyUriToTempFile
import java.io.File

@Composable
fun VoiceRegistrationDialog(
    onDismiss: () -> Unit,
    onGenerateVoice: (File, String) -> Unit
) {
    val context = LocalContext.current
    var voiceName by remember { mutableStateOf("") }
    var selectedFile by remember { mutableStateOf<File?>(null) }

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            // 아래 3번에 작성된 유틸리티 함수 사용
            selectedFile = copyUriToTempFile(context, it)
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("새 목소리 등록", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = voiceName,
                    onValueChange = { voiceName = it },
                    label = { Text("목소리 이름 (예: 친구 목소리)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedButton(
                    onClick = { filePicker.launch("audio/*") },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (selectedFile == null) "음성 파일 선택" else "파일 선택됨: ${selectedFile?.name}")
                }

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) { Text("취소") }
                    Button(
                        onClick = {
                            if (voiceName.isNotBlank() && selectedFile != null) {
                                onGenerateVoice(selectedFile!!, voiceName)
                            } else {
                                Toast.makeText(context, "이름을 입력하고 파일을 선택해주세요.", Toast.LENGTH_SHORT)
                                    .show()
                            }
                        },
                        enabled = voiceName.isNotBlank() && selectedFile != null
                    ) {
                        Text("보이스 생성")
                    }
                }
            }
        }
    }
}