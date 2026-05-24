package com.example.maw9oot.presentation.ui.screens

import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.dokar.sheets.rememberBottomSheetState
import com.example.maw9oot.R
import com.example.maw9oot.presentation.ui.components.home.PrayerBottomSheet
import com.example.maw9oot.presentation.ui.components.home.PrayerButton
import com.example.maw9oot.data.enums.Prayer
import com.example.maw9oot.data.enums.PrayerStatus
import com.example.maw9oot.presentation.viewmodel.HomeViewModel
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    homeViewModel: HomeViewModel = hiltViewModel()
) {
    val scope = rememberCoroutineScope()
    val sheetState = rememberBottomSheetState()

    val selectedPrayer by homeViewModel.selectedPrayer.observeAsState(Prayer.FAJR)
    val prayerStatuses by homeViewModel.prayerStatuses.observeAsState(emptyMap())
    val prayerTimes by homeViewModel.prayerTimeForDate.observeAsState(emptyMap())
    val selectedDate by homeViewModel.selectedDate.observeAsState("")

    val appDateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.ROOT)
    val currentDate = LocalDate.now().format(appDateFormat)

    val currentTimeMillis = System.currentTimeMillis()
    val datePickerState = rememberDatePickerState(initialSelectedDateMillis = currentTimeMillis)
    var showDialog by remember { mutableStateOf(false) }

    fun showSheet(prayer: Prayer) {
        homeViewModel.selectPrayer(prayer)
        scope.launch { sheetState.expand(animate = true) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(
                onClick = {
                    try {
                        val prevDate = LocalDate.parse(selectedDate, appDateFormat).minusDays(1)
                        homeViewModel.updateSelectedDate(prevDate.format(appDateFormat))
                    } catch (e: Exception) {
                        Log.e("HomeScreen", "Error parsing date: $selectedDate", e)
                    }
                }
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.baseline_navigate_before_24),
                    contentDescription = "Previous Day",
                    modifier = Modifier.size(35.dp)
                )
            }

            Text(
                text = selectedDate,
                modifier = Modifier.clickable { showDialog = true },
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )

            IconButton(
                onClick = {
                    try {
                        val nextDate = LocalDate.parse(selectedDate, appDateFormat).plusDays(1)
                        val nextDateStr = nextDate.format(appDateFormat)
                        if (nextDateStr <= currentDate) {
                            homeViewModel.updateSelectedDate(nextDateStr)
                        }
                    } catch (e: Exception) {
                        Log.e("HomeScreen", "Error parsing date: $selectedDate", e)
                    }
                },
                enabled = selectedDate != currentDate
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.baseline_navigate_next_24),
                    contentDescription = "Next Day",
                    modifier = Modifier.size(35.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))


        Prayer.entries.forEach { prayer ->
            PrayerButton(
                prayerName = prayer.prayerName,
                prayerIcon = {
                    Image(
                        painter = painterResource(id = prayer.icon),
                        contentDescription = prayer.prayerName,
                        modifier = Modifier.size(50.dp)
                    )
                },
                prayerStatus = prayerStatuses[prayer] ?: PrayerStatus.NONE,
                prayerTime = prayerTimes[prayer] ?: "--:--",
            ) {
                showSheet(prayer)
            }
        }

        PrayerBottomSheet(
            prayer = selectedPrayer,
            sheetState = sheetState,
            currentStatus = prayerStatuses[selectedPrayer] ?: PrayerStatus.NONE,
            onStatusChange = { status ->
                val statusEnum = PrayerStatus.fromDisplayName(status)
                statusEnum.let {
                    homeViewModel.updateStatus(selectedPrayer, it, selectedDate)
                }
            }
        )
    }

    if (showDialog) {
        DatePickerDialog(
            onDismissRequest = { showDialog = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                         // LocalDate.ofEpochDay expects days, millis to days:
                        val newDate = LocalDate.ofEpochDay(millis / (24 * 60 * 60 * 1000))
                            .format(appDateFormat)
                        homeViewModel.updateSelectedDate(newDate)
                    }
                    showDialog = false
                }) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text("Cancel")
                }
            }
        ) {
            DatePicker(
                state = datePickerState,
                showModeToggle = true,
            )
        }
    }
}
