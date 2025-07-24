package it.unibo.collektive.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.width
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.unit.sp
import it.unibo.collektive.ui.theme.LightGray
import it.unibo.collektive.ui.theme.Purple40
import it.unibo.collektive.ui.theme.Purple80
import it.unibo.collektive.viewmodels.CommunicationSettingViewModel

@Composable
fun CommunicationSettingSelector(communicationSettingViewModel: CommunicationSettingViewModel) {
    var time by remember { mutableFloatStateOf(5f) }
    var distance by remember { mutableFloatStateOf(2000f) }

    LaunchedEffect(distance) {
        communicationSettingViewModel.setDistance(distance)
    }
    LaunchedEffect(time) {
        communicationSettingViewModel.setTime(time)
    }

    Column(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(modifier = Modifier.width(50.dp)){
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Start,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(text = "for", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }
            }
            Text(text = "5 sc")
            Slider(
                value = time,
                onValueChange = { time = it },
                valueRange = 5f..60f,
                modifier = Modifier.weight(1f),
                colors = SliderDefaults.colors(
                    thumbColor = Purple80,
                    activeTrackColor = Purple40,
                    inactiveTrackColor = LightGray
                )
            )
            Text(text = "1 min")
        }
        Text(text = communicationSettingViewModel.formatTime(time), textAlign = TextAlign.Center)

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(modifier = Modifier.width(50.dp)){
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Start,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(text = "to", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }
            }
            Text(text = "1 mt")
            Slider(
                value = distance,
                onValueChange = { distance = it },
                valueRange = 1f..10_000f,
                modifier = Modifier.weight(1f),
                colors = SliderDefaults.colors(
                    thumbColor = Purple80,
                    activeTrackColor = Purple40,
                    inactiveTrackColor = LightGray
                )
            )
            Text(text = "10 km")
        }
        Text(text = communicationSettingViewModel.formatDistance(distance), textAlign = TextAlign.Center)
    }
}
