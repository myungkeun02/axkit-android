package dev.axkit.app.ui.design

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.automirrored.rounded.FormatListBulleted
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.ListAlt
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Computer
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.PanTool
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.GpsFixed
import androidx.compose.material.icons.rounded.PlaylistAddCheck
import androidx.compose.material.icons.rounded.FindInPage
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.Update
import androidx.compose.ui.graphics.vector.ImageVector

/** SF Symbol names (the server sends them for server-drawn screens, R9) mapped to the closest Material icon. */
object SfIcons {
    fun of(name: String?): ImageVector = when (name) {
        "sparkles", "wand.and.stars" -> Icons.Rounded.AutoAwesome
        "globe" -> Icons.Rounded.Public
        "apple.terminal" -> Icons.Rounded.Terminal
        "doc.badge.clock" -> Icons.Rounded.History
        "clock.arrow.circlepath" -> Icons.Rounded.Update
        "checklist", "checkmark.circle.badge.questionmark" -> Icons.Rounded.Checklist
        "chart.bar", "chart.bar.fill" -> Icons.Rounded.BarChart
        "bolt.horizontal.circle", "bolt" -> Icons.Rounded.Bolt
        "list.number", "list.bullet" -> Icons.AutoMirrored.Rounded.FormatListBulleted
        "wonsign.circle" -> Icons.Rounded.Payments
        "info.circle" -> Icons.Rounded.Info
        "shippingbox" -> Icons.Rounded.Inventory2
        "calendar" -> Icons.Rounded.CalendarMonth
        "doc", "doc.text" -> Icons.Rounded.Description
        "doc.text.magnifyingglass" -> Icons.Rounded.FindInPage
        "hammer" -> Icons.Rounded.Build
        "lock.shield" -> Icons.Rounded.Security
        "macmini", "macmini.fill" -> Icons.Rounded.Computer
        "link" -> Icons.Rounded.Link
        "person.2.fill" -> Icons.Rounded.Group
        "bubble.left", "bubble.left.and.bubble.right" -> Icons.Rounded.Forum
        "checkmark.seal" -> Icons.Rounded.Verified
        "magnifyingglass" -> Icons.Rounded.Search
        "qrcode.viewfinder" -> Icons.Rounded.QrCodeScanner
        "exclamationmark.triangle.fill" -> Icons.Rounded.Warning
        "wifi.exclamationmark" -> Icons.Rounded.WifiOff
        "camera.fill" -> Icons.Rounded.CameraAlt
        "photo" -> Icons.Rounded.Image
        "checkmark" -> Icons.Rounded.Check
        "xmark" -> Icons.Rounded.Close
        "plus" -> Icons.Rounded.Add
        "hand.raised.fill" -> Icons.Rounded.PanTool
        "mic.circle.fill" -> Icons.Rounded.Mic
        "stop.circle.fill" -> Icons.Rounded.Stop
        "scope" -> Icons.Rounded.GpsFixed
        "ellipsis" -> Icons.Rounded.MoreHoriz
        "chevron.down" -> Icons.Rounded.KeyboardArrowDown
        "chevron.up" -> Icons.Rounded.KeyboardArrowUp
        "chevron.left" -> Icons.AutoMirrored.Rounded.KeyboardArrowLeft
        "chevron.right" -> Icons.AutoMirrored.Rounded.KeyboardArrowRight
        "arrow.right" -> Icons.AutoMirrored.Rounded.ArrowForward
        "list.bullet.rectangle" -> Icons.AutoMirrored.Rounded.ListAlt
        "text.bubble" -> Icons.AutoMirrored.Rounded.Chat
        "checklist.checked" -> Icons.Rounded.PlaylistAddCheck
        else -> Icons.Rounded.Widgets
    }
}
