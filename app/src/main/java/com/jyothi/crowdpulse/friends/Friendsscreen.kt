package com.jyothi.crowdpulse.friends

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jyothi.crowdpulse.location.LocationUtils

@Composable
fun FriendsScreen(
    friends: List<Friend>,
    nearbyNonFriends: List<Friend>,
    myLat: Double,
    myLon: Double,
    onFriendClick: (Friend) -> Unit,
    onAddFriend: (Friend) -> Unit,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0A0A))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
            Text("Friends & Messages",
                fontSize   = 20.sp,
                fontWeight = FontWeight.Bold,
                color      = Color.White,
                modifier   = Modifier.padding(start = 8.dp))
        }

        LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {

            if (friends.isNotEmpty()) {
                item { SectionLabel("Your Friends") }
                items(friends) { friend ->
                    FriendRow(
                        friend  = friend,
                        myLat   = myLat,
                        myLon   = myLon,
                        showAdd = false,
                        onClick = { onFriendClick(friend) },
                        onAdd   = {}
                    )
                }
            }

            if (nearbyNonFriends.isNotEmpty()) {
                item {
                    SectionLabel("Nearby CrowdPulse Users")
                    Text("Tap + Add to save as friend",
                        fontSize = 11.sp,
                        color    = Color(0xFF666666),
                        modifier = Modifier.padding(bottom = 8.dp))
                }
                items(nearbyNonFriends) { user ->
                    FriendRow(
                        friend  = user,
                        myLat   = myLat,
                        myLon   = myLon,
                        showAdd = true,
                        onClick = {},
                        onAdd   = { onAddFriend(user) }
                    )
                }
            }

            if (friends.isEmpty() && nearbyNonFriends.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(top = 80.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("📡", fontSize = 48.sp)
                            Spacer(Modifier.height(16.dp))
                            Text("No one nearby yet",
                                color = Color.Gray, fontSize = 16.sp)
                            Text("CrowdPulse users appear here automatically",
                                color    = Color(0xFF555555),
                                fontSize = 12.sp,
                                modifier = Modifier.padding(top = 8.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SectionLabel(text: String) {
    Text(text,
        fontSize   = 11.sp,
        fontWeight = FontWeight.SemiBold,
        color      = Color(0xFF888888),
        modifier   = Modifier.padding(top = 20.dp, bottom = 8.dp))
}

@Composable
fun FriendRow(
    friend: Friend,
    myLat: Double,
    myLon: Double,
    showAdd: Boolean,
    onClick: () -> Unit,
    onAdd: () -> Unit
) {
    val hasLocation = friend.lastLat != 0.0 && friend.lastLon != 0.0
            && myLat != 0.0 && myLon != 0.0

    val distance  = if (hasLocation)
        LocationUtils.distanceMetres(myLat, myLon, friend.lastLat, friend.lastLon)
    else -1.0
    val bearing   = if (hasLocation)
        LocationUtils.bearingDegrees(myLat, myLon, friend.lastLat, friend.lastLon)
    else 0.0
    val arrow     = if (hasLocation) LocationUtils.bearingToArrow(bearing)     else ""
    val direction = if (hasLocation) LocationUtils.bearingToDirection(bearing) else ""
    val distText  = if (hasLocation) LocationUtils.formatDistance(distance)    else ""
    val proximity = if (hasLocation) LocationUtils.proximityLabel(distance)    else ""

    val distanceColor = when {
        !hasLocation    -> Color(0xFF666666)
        distance < 50   -> Color(0xFF44FF88)
        distance < 200  -> Color(0xFFFFCC00)
        distance < 1000 -> Color(0xFFFF8800)
        else            -> Color(0xFF888888)
    }

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick, enabled = !showAdd)
                .padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Avatar
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF1A2A3A)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    friend.name.firstOrNull()?.uppercase() ?: "?",
                    color      = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize   = 20.sp
                )
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(friend.name,
                    color      = Color.White,
                    fontSize   = 15.sp,
                    fontWeight = FontWeight.SemiBold)

                Spacer(Modifier.height(4.dp))

                if (hasLocation && friend.isNearby) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(arrow,
                            fontSize = 16.sp,
                            color    = distanceColor)
                        Spacer(Modifier.width(4.dp))
                        Text(distText,
                            fontSize   = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color      = distanceColor)
                        Spacer(Modifier.width(6.dp))
                        // Only show compass direction when far enough for GPS to be reliable
                        if (distance > 30) {
                            Text("• $direction",
                                fontSize = 12.sp,
                                color    = Color(0xFF888888))
                        } else {
                            Text("• Very close",
                                fontSize = 12.sp,
                                color    = Color(0xFF888888))
                        }
                    }
                    Text(proximity,
                        fontSize = 11.sp,
                        color    = Color(0xFF555555))
                } else if (friend.isNearby) {
                    Text("📍 Nearby — location updating...",
                        fontSize = 12.sp,
                        color    = Color(0xFF44FF88))
                } else {
                    Text("Last seen recently",
                        fontSize = 12.sp,
                        color    = Color(0xFF555555))
                }
            }

            if (showAdd) {
                TextButton(onClick = onAdd) {
                    Text("+ Add",
                        color    = Color(0xFF4488FF),
                        fontSize = 13.sp)
                }
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("💬", fontSize = 20.sp)
                    Text("chat", fontSize = 10.sp, color = Color(0xFF555555))
                }
            }
        }

        if (hasLocation && friend.isNearby && distance > 0) {
            val progress = (1.0 - (distance / 1000.0).coerceIn(0.0, 1.0)).toFloat()
            LinearProgressIndicator(
                progress   = { progress },
                modifier   = Modifier.fillMaxWidth().height(2.dp),
                color      = distanceColor,
                trackColor = Color(0xFF1A1A1A)
            )
        }

        HorizontalDivider(color = Color(0xFF111111), thickness = 0.5.dp)
    }
}