/*
 * This file is part of Bura.
 *
 * Bura is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 *
 * Bura is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with Bura. If not, see <https://www.gnu.org/licenses/>.
 */

package com.davidtakac.bura.summary.visibility

import com.davidtakac.bura.forecast.ForecastResult
import com.davidtakac.bura.place.Location
import com.davidtakac.bura.units.Units
import com.davidtakac.bura.visibility.Visibility
import com.davidtakac.bura.visibility.VisibilityRepository
import java.time.Instant

class GetVisibilitySummary(private val repo: VisibilityRepository) {
    suspend operator fun invoke(
        location: Location,
        units: Units,
        now: Instant
    ): ForecastResult<VisibilitySummary> {
        val period =
            repo.period(location, units) ?: return ForecastResult.FailedToDownload
        return ForecastResult.Success(
            VisibilitySummary(
                now = period[now]?.visibility ?: return ForecastResult.Outdated
            )
        )
    }
}

data class VisibilitySummary(val now: Visibility)