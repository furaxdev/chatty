package com.chatty.fr.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Categories: List<Pair<String, List<String>>> = listOf(
    "😀" to "😀 😃 😄 😁 😆 😅 🤣 😂 🙂 🙃 😉 😊 😇 🥰 😍 🤩 😘 😗 😚 😙 😋 😛 😜 🤪 😝 🤑 🤗 🤭 🤫 🤔 🤐 🤨 😐 😑 😶 😏 😒 🙄 😬 🤥 😌 😔 😪 🤤 😴 😷 🤒 🤕 🤢 🤮 🥵 🥶 🥴 😵 🤯 🤠 🥳 😎 🤓 🧐 😕 😟 🙁 😮 😯 😲 😳 🥺 😦 😧 😨 😰 😥 😢 😭 😱 😖 😣 😞 😓 😩 😫 🥱 😤 😡 😠 🤬 😈 👿 💀 💩 🤡 👻 👽 🤖 😺 😸 😹 😻 😼 😽 🙀 😿 😾",
    "👋" to "👋 🤚 🖐️ ✋ 🖖 👌 🤌 🤏 ✌️ 🤞 🤟 🤘 🤙 👈 👉 👆 👇 ☝️ 👍 👎 ✊ 👊 🤛 🤜 👏 🙌 👐 🤲 🤝 🙏 ✍️ 💅 🤳 💪 🦾 🧠 👀 👁️ 👅 👄 💋 🫶 🙋 🙆 🙅 🤷 🤦 🙇 💃 🕺 👯 🧘 🏃 🚶",
    "❤️" to "❤️ 🧡 💛 💚 💙 💜 🖤 🤍 🤎 💔 ❣️ 💕 💞 💓 💗 💖 💘 💝 💟 ♥️ 💯 💢 💥 💫 💦 💨 🕳️ 💬 💭 💤 ✨ 🌟 ⭐ 🔥 🎉 🎊 🎈 🎁 🏆 🥇 ✅ ❌ ❓ ❗ ‼️ ⚠️",
    "🐶" to "🐶 🐱 🐭 🐹 🐰 🦊 🐻 🐼 🐨 🐯 🦁 🐮 🐷 🐸 🐵 🙈 🙉 🙊 🐔 🐧 🐦 🐤 🦆 🦅 🦉 🦇 🐺 🐗 🐴 🦄 🐝 🐛 🦋 🐌 🐞 🐢 🐍 🦖 🐙 🦑 🦀 🐠 🐬 🐳 🦈 🐊 🐘 🦒 🐕 🐈 🌵 🌲 🌴 🌱 🍀 🍁 🍄 🌷 🌹 🌻 🌸 🌈 ☀️ 🌙 ⭐ ⚡ ❄️ ☃️ 🌊",
    "🍕" to "🍏 🍎 🍐 🍊 🍋 🍌 🍉 🍇 🍓 🍒 🍑 🥭 🍍 🥥 🥝 🍅 🥑 🍆 🥕 🌽 🌶️ 🥐 🍞 🧀 🥚 🍳 🥓 🍔 🍟 🍕 🌭 🥪 🌮 🌯 🥗 🍝 🍜 🍣 🍱 🍤 🍦 🍩 🍪 🎂 🍰 🧁 🍫 🍬 🍭 ☕ 🍵 🧃 🥤 🍺 🍻 🥂 🍷 🍸 🍹",
    "⚽" to "⚽ 🏀 🏈 ⚾ 🎾 🏐 🏉 🎱 🏓 🏸 🥊 🥋 ⛳ 🎿 🛹 🎮 🕹️ 🎲 🎯 🎳 🎸 🎹 🎤 🎧 🎬 🎨 🚗 🚕 🚌 🏎️ 🚓 🚑 🚒 🚲 🛵 🏍️ ✈️ 🚀 🛸 🚁 ⛵ 🚢 🏠 🏢 🏰 🗼 🗽 ⛺ 🏖️ 🏝️ 📱 💻 ⌚ 📷 💡 📚 ✏️ 📌 📎 🔑 🔒 💰 💳 ⏰ 📅",
).map { (icon, list) -> icon to list.split(' ').filter { it.isNotBlank() } }

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun EmojiPanel(recent: List<String>, onPick: (String) -> Unit, modifier: Modifier = Modifier) {
    val tabs = remember(recent) { if (recent.isEmpty()) Categories else listOf("🕘" to recent) + Categories }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val current = tabs[tab.coerceIn(0, tabs.lastIndex)]
    Column(modifier.fillMaxWidth().height(280.dp).background(MaterialTheme.colorScheme.surfaceContainer)) {
        PrimaryScrollableTabRow(selectedTabIndex = tab.coerceIn(0, tabs.lastIndex), edgePadding = 8.dp, containerColor = MaterialTheme.colorScheme.surfaceContainer) {
            tabs.forEachIndexed { i, (icon, _) ->
                Tab(selected = i == tab, onClick = { tab = i }, text = { Text(icon, fontSize = 20.sp) })
            }
        }
        LazyVerticalGrid(GridCells.Adaptive(46.dp), Modifier.padding(horizontal = 6.dp)) {
            items(current.second) { emoji ->
                Box(
                    Modifier
                        .height(46.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onPick(emoji) },
                    contentAlignment = Alignment.Center,
                ) { Text(emoji, fontSize = 26.sp) }
            }
        }
    }
}
