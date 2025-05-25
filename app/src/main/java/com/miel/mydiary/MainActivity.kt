package com.miel.mydiary

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.List
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.room.*
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.miel.mydiary.ui.theme.MyDiaryTheme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

// Preview를 위한 flowOf 임포트 (Preview 함수에서만 필요)
import kotlinx.coroutines.flow.flowOf


// Room 데이터베이스를 위한 일기 항목 데이터 클래스
@Entity(tableName = "diary_entries")
data class DiaryEntry(
    @PrimaryKey(autoGenerate = true) val id: Int = 0, // 고유 ID
    val date: String,
    val content: String
)

// DAO (Data Access Object) 인터페이스
@Dao
interface DiaryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: DiaryEntry)

    @Query("SELECT * FROM diary_entries ORDER BY id DESC") // 최신 일기가 먼저 오도록 정렬
    fun getAllEntries(): Flow<List<DiaryEntry>> // Flow를 사용하여 데이터 변경을 관찰
}

// Room 데이터베이스 클래스
@Database(entities = [DiaryEntry::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun diaryDao(): DiaryDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: android.content.Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "diary_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}

class MainActivity : ComponentActivity() {
    // 데이터베이스 인스턴스를 초기화합니다.
    private lateinit var database: AppDatabase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        database = AppDatabase.getDatabase(applicationContext) // 데이터베이스 초기화

        setContent {
            MyDiaryTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    // DiaryApp에 DAO를 전달합니다.
                    DiaryApp(diaryDao = database.diaryDao())
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiaryApp(diaryDao: DiaryDao) {
    // 현재 선택된 탭의 인덱스를 관리하는 상태
    var selectedTabIndex by remember { mutableStateOf(0) }
    // Room DB에서 일기 항목들을 Flow로 가져와 상태로 변환합니다.
    val diaryEntries by diaryDao.getAllEntries().collectAsState(initial = emptyList())
    val coroutineScope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("나의 일기장") }
            )
        },
        bottomBar = {
            // 하단 탭 바 구현
            NavigationBar {
                // '쓰기' 탭 아이템
                NavigationBarItem(
                    selected = selectedTabIndex == 0,
                    onClick = { selectedTabIndex = 0 },
                    icon = { Icon(Icons.Default.Create, contentDescription = "쓰기") },
                    label = { Text("쓰기") }
                )
                // '기록' 탭 아이템
                NavigationBarItem(
                    selected = selectedTabIndex == 1,
                    onClick = { selectedTabIndex = 1 },
                    icon = { Icon(Icons.Default.List, contentDescription = "기록") },
                    label = { Text("기록") }
                )
            }
        }
    ) { paddingValues ->
        // 탭 내용 표시
        Column(modifier = Modifier.padding(paddingValues)) {
            when (selectedTabIndex) {
                0 -> WriteDiaryTab(onSave = { content ->
                    // 현재 날짜와 시간을 포맷하여 일기 항목을 생성하고 DB에 삽입합니다.
                    val currentDate = SimpleDateFormat("yyyy.MM.dd HH:mm", Locale.getDefault()).format(Date())
                    val newEntry = DiaryEntry(date = currentDate, content = content)
                    coroutineScope.launch {
                        diaryDao.insert(newEntry)
                    }
                })
                1 -> RecordsTab(diaryEntries = diaryEntries)
            }
        }
    }
}

@Composable
fun WriteDiaryTab(onSave: (String) -> Unit) {
    // 일기 내용을 입력받는 TextField의 상태
    var diaryContent by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // 일기 내용을 입력하는 TextField
        OutlinedTextField(
            value = diaryContent,
            onValueChange = { diaryContent = it },
            label = { Text("오늘의 일기를 작성해주세요...") },
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f) // 남은 공간을 모두 차지하도록 설정
        )
        Spacer(modifier = Modifier.height(16.dp))
        // 저장 버튼
        Button(
            onClick = {
                if (diaryContent.isNotBlank()) {
                    onSave(diaryContent)
                    diaryContent = "" // 저장 후 입력 필드 초기화
                }
            },
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(12.dp)
        ) {
            Text("일기 저장")
        }
    }
}

@Composable
fun RecordsTab(diaryEntries: List<DiaryEntry>) {
    // 일기 기록들을 표시하는 LazyColumn
    if (diaryEntries.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = androidx.compose.ui.Alignment.Center
        ) {
            Text("아직 작성된 일기가 없습니다.", style = MaterialTheme.typography.headlineSmall)
        }
    } else {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp) // 항목 간 간격
        ) {
            items(diaryEntries) { entry ->
                // 각 일기 항목을 카드 형태로 표시
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = entry.date,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = entry.content,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun DiaryAppPreview() {
    MyDiaryTheme {
        // Preview에서는 DAO를 전달할 수 없으므로, 간단한 더미 리스트를 사용합니다.
        // 실제 앱에서는 Room DB에서 데이터를 가져옵니다.
        val dummyEntries = remember { mutableStateListOf<DiaryEntry>() }
        dummyEntries.add(DiaryEntry(date = "2023.01.01 10:00", content = "첫 번째 일기 내용입니다."))
        dummyEntries.add(DiaryEntry(date = "2023.01.02 11:30", content = "두 번째 일기 내용입니다."))
        // Preview에서는 AppDatabase.getDatabase(LocalContext.current).diaryDao()를 직접 호출할 수 없으므로,
        // 실제 앱의 동작을 완전히 반영하기 어렵습니다.
        // 이 부분은 실제 앱 실행 시에만 유효합니다.
        // 여기서는 컴파일 오류를 피하기 위해 임시로 빈 DAO를 전달합니다.
        // 실제 앱에서는 MainActivity에서 생성된 database.diaryDao()를 전달합니다.
        DiaryApp(diaryDao = object : DiaryDao {
            override suspend fun insert(entry: DiaryEntry) {}
            override fun getAllEntries(): Flow<List<DiaryEntry>> = flowOf(dummyEntries.toList())
        })
    }
}

