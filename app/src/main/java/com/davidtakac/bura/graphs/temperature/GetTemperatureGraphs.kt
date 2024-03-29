/*
 * This file is part of Bura.
 *
 * Bura is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 *
 * Bura is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with Bura. If not, see <https://www.gnu.org/licenses/>.
 */

package com.davidtakac.bura.graphs.temperature

import com.davidtakac.bura.forecast.ForecastResult
import com.davidtakac.bura.temperature.Temperature
import com.davidtakac.bura.temperature.TemperatureMoment
import com.davidtakac.bura.temperature.TemperaturePeriod
import com.davidtakac.bura.temperature.TemperatureRepository
import com.davidtakac.bura.units.Units
import com.davidtakac.bura.condition.Condition
import com.davidtakac.bura.condition.ConditionMoment
import com.davidtakac.bura.condition.ConditionPeriod
import com.davidtakac.bura.condition.ConditionRepository
import com.davidtakac.bura.graphs.common.GraphTime
import com.davidtakac.bura.place.Location
import java.time.Instant
import java.time.LocalDate

class GetTemperatureGraphs(
    private val tempRepo: TemperatureRepository,
    private val descRepo: ConditionRepository,
) {
    suspend operator fun invoke(
        location: Location,
        units: Units,
        now: Instant
    ): ForecastResult<TemperatureGraphs> {
        val tempPeriod = tempRepo.period(location, units) ?: return ForecastResult.FailedToDownload
        val descPeriod = descRepo.period(location, units) ?: return ForecastResult.FailedToDownload
        val tempDays = tempPeriod.daysFrom(now, location.timeZone) ?: return ForecastResult.Outdated
        val conditionDays =
            descPeriod.daysFrom(now, location.timeZone) ?: return ForecastResult.Outdated
        return ForecastResult.Success(
            data = TemperatureGraphs(
                minTemp = tempPeriod.minimum,
                maxTemp = tempPeriod.maximum,
                graphs = getGraphs(
                    coords = location,
                    now = now,
                    tempDays = tempDays,
                    conditionDays = conditionDays
                )
            )
        )
    }

    private fun getGraphs(
        coords: Location,
        now: Instant,
        tempDays: List<TemperaturePeriod>,
        conditionDays: List<ConditionPeriod>
    ): List<TemperatureGraph> = buildList {
        for (i in tempDays.indices) {
            add(
                getGraph(
                    coords = coords,
                    now = now,
                    tempDay = tempDays[i],
                    conditionDay = conditionDays[i],
                    nextTempDay = tempDays.getOrNull(i + 1),
                    nextConditionDay = conditionDays.getOrNull(i + 1)
                )
            )
        }
    }

    private fun getGraph(
        coords: Location,
        now: Instant,
        tempDay: TemperaturePeriod,
        conditionDay: ConditionPeriod,
        nextTempDay: TemperaturePeriod?,
        nextConditionDay: ConditionPeriod?
    ): TemperatureGraph {
        val minTempMoment = tempDay.reversed().minBy { it.temperature }
        val maxTempMoment = tempDay.reversed().maxBy { it.temperature }
        return TemperatureGraph(
            day = tempDay.first().hour.atZone(coords.timeZone).toLocalDate(),
            points = buildList {
                for (i in tempDay.indices) {
                    add(
                        getPoint(
                            coords = coords,
                            now = now,
                            tempMoment = tempDay[i],
                            minTempMoment = minTempMoment,
                            maxTempMoment = maxTempMoment,
                            conditionMoment = conditionDay[i]
                        )
                    )
                }
                val firstTempTomorrow = nextTempDay?.firstOrNull()
                if (firstTempTomorrow != null) {
                    // The periods must match, so if there is a first temp tomorrow, there
                    // must be a matching condition tomorrow too
                    val firstConditionTomorrow = nextConditionDay!!.first()
                    add(
                        getPoint(
                            coords = coords,
                            now = now,
                            tempMoment = firstTempTomorrow,
                            minTempMoment = minTempMoment,
                            maxTempMoment = maxTempMoment,
                            conditionMoment = firstConditionTomorrow
                        )
                    )
                }
            }
        )
    }

    private fun getPoint(
        coords: Location,
        now: Instant,
        tempMoment: TemperatureMoment,
        minTempMoment: TemperatureMoment,
        maxTempMoment: TemperatureMoment,
        conditionMoment: ConditionMoment
    ): TemperatureGraphPoint = TemperatureGraphPoint(
        time = GraphTime(tempMoment.hour, now, coords.timeZone),
        temperature = GraphTemperature(
            value = tempMoment.temperature,
            meta = getTempMeta(minTempMoment, maxTempMoment, tempMoment)
        ),
        condition = conditionMoment.condition,
    )

    private fun getTempMeta(
        minTempMoment: TemperatureMoment,
        maxTempMoment: TemperatureMoment,
        tempMoment: TemperatureMoment
    ): GraphTemperature.Meta =
        when {
            minTempMoment == maxTempMoment -> GraphTemperature.Meta.Regular
            tempMoment == minTempMoment -> GraphTemperature.Meta.Minimum
            tempMoment == maxTempMoment -> GraphTemperature.Meta.Maximum
            else -> GraphTemperature.Meta.Regular
        }
}

data class TemperatureGraphs(
    val minTemp: Temperature,
    val maxTemp: Temperature,
    val graphs: List<TemperatureGraph>
)

data class TemperatureGraph(
    val day: LocalDate,
    val points: List<TemperatureGraphPoint>
)

data class TemperatureGraphPoint(
    val time: GraphTime,
    val temperature: GraphTemperature,
    val condition: Condition
)

data class GraphTemperature(
    val value: Temperature,
    val meta: Meta
) {
    enum class Meta {
        Minimum, Maximum, Regular
    }
}