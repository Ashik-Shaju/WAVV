/*
 * Copyright 2025 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package androidx.compose.material.icons.outlined

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.materialIcon
import androidx.compose.material.icons.materialPath
import androidx.compose.ui.graphics.vector.ImageVector

public val Icons.Outlined.PrivacyTip: ImageVector
    get() {
        if (_privacyTip != null) {
            return _privacyTip!!
        }
        _privacyTip = materialIcon(name = "Outlined.PrivacyTip") {
            materialPath {
                moveTo(12.0f, 3.19f)
                lineToRelative(7.0f, 3.11f)
                verticalLineTo(11.0f)
                curveToRelative(0.0f, 4.52f, -2.98f, 8.69f, -7.0f, 9.93f)
                curveTo(7.98f, 19.69f, 5.0f, 15.52f, 5.0f, 11.0f)
                verticalLineTo(6.3f)
                lineTo(12.0f, 3.19f)
                moveTo(12.0f, 1.0f)
                lineTo(3.0f, 5.0f)
                verticalLineToRelative(6.0f)
                curveToRelative(0.0f, 5.55f, 3.84f, 10.74f, 9.0f, 12.0f)
                curveToRelative(5.16f, -1.26f, 9.0f, -6.45f, 9.0f, -12.0f)
                verticalLineTo(5.0f)
                lineTo(12.0f, 1.0f)
                lineTo(12.0f, 1.0f)
                close()
                moveTo(11.0f, 7.0f)
                horizontalLineToRelative(2.0f)
                verticalLineToRelative(2.0f)
                horizontalLineToRelative(-2.0f)
                verticalLineTo(7.0f)
                close()
                moveTo(11.0f, 11.0f)
                horizontalLineToRelative(2.0f)
                verticalLineToRelative(6.0f)
                horizontalLineToRelative(-2.0f)
                verticalLineTo(11.0f)
                close()
            }
        }
        return _privacyTip!!
    }

private var _privacyTip: ImageVector? = null
