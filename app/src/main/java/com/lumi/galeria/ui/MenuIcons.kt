package com.lumi.galeria.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Redo
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Cameraswitch
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Compress
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DocumentScanner
import androidx.compose.material.icons.outlined.Draw
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.FlashAuto
import androidx.compose.material.icons.outlined.FlashOff
import androidx.compose.material.icons.outlined.FlashOn
import androidx.compose.material.icons.outlined.FlashlightOn
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.LocationOff
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.RotateRight
import androidx.compose.material.icons.outlined.ScreenRotation
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Slideshow
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.Wallpaper
import androidx.compose.ui.graphics.vector.ImageVector

/*
 * Los iconos de Lumi, todos del mismo juego (Material, estilo de línea): mismo grosor, mismas
 * esquinas y mismo tamaño visual en toda la app. Los nombres se mantienen de cuando eran dibujos
 * propios, así no cambia nada donde se usan.
 */

/** Una foto. */
val PictureIcon: ImageVector get() = Icons.Outlined.Image
val RotateIcon: ImageVector get() = Icons.Outlined.RotateRight
val TextIcon: ImageVector get() = Icons.Outlined.TextFields
val PersonIcon: ImageVector get() = Icons.Outlined.Person
val CopyIcon: ImageVector get() = Icons.Outlined.ContentCopy
val LandscapeIcon: ImageVector get() = Icons.Outlined.ScreenRotation
val ScanIcon: ImageVector get() = Icons.Outlined.DocumentScanner
val ShrinkIcon: ImageVector get() = Icons.Outlined.Compress
val PenIcon: ImageVector get() = Icons.Outlined.Draw
val SlidesIcon: ImageVector get() = Icons.Outlined.Slideshow
val InfoIcon: ImageVector get() = Icons.Outlined.Info
val NoPlaceIcon: ImageVector get() = Icons.Outlined.LocationOff
val OpenWithIcon: ImageVector get() = Icons.Outlined.OpenInNew
val CoverIcon: ImageVector get() = Icons.Outlined.Wallpaper
val UndoIcon: ImageVector get() = Icons.AutoMirrored.Outlined.Undo
val RedoIcon: ImageVector get() = Icons.AutoMirrored.Outlined.Redo
val MovieIcon: ImageVector get() = Icons.Outlined.Movie

/** Lo nuevo, Lumi Auto. */
val SparkIcon: ImageVector get() = Icons.Outlined.AutoAwesome
val SearchLineIcon: ImageVector get() = Icons.Outlined.Search
val CameraLineIcon: ImageVector get() = Icons.Outlined.PhotoCamera
val LockLineIcon: ImageVector get() = Icons.Outlined.Lock
val TrophyIcon: ImageVector get() = Icons.Outlined.EmojiEvents

/** Cámara. */
val BoltIcon: ImageVector get() = Icons.Outlined.FlashOn
val BoltOffIcon: ImageVector get() = Icons.Outlined.FlashOff
val FlashAutoIcon: ImageVector get() = Icons.Outlined.FlashAuto
val TimerIcon: ImageVector get() = Icons.Outlined.Timer
val GearIcon: ImageVector get() = Icons.Outlined.Settings
val CloseLineIcon: ImageVector get() = Icons.Outlined.Close
val TorchIcon: ImageVector get() = Icons.Outlined.FlashlightOn
val HistoryIcon: ImageVector get() = Icons.Outlined.History
val FlipCameraIcon: ImageVector get() = Icons.Outlined.Cameraswitch
val PauseIcon: ImageVector get() = Icons.Outlined.Pause
val PlayLineIcon: ImageVector get() = Icons.Outlined.PlayArrow
val HeartIcon: ImageVector get() = Icons.Outlined.FavoriteBorder
val TrashIcon: ImageVector get() = Icons.Outlined.Delete
val FolderIcon: ImageVector get() = Icons.Outlined.Folder
val PaletteIcon: ImageVector get() = Icons.Outlined.Palette
