package io.github.lianeheidemann.roverquest.core.floor

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class FloorSetupTest {

    private val withRover = FloorSetup().confirmArea().withRover()

    @Test
    fun `phases follow rover then destination`() {
        assertEquals(SetupPhase.CONFIRM_AREA, FloorSetup().phase)
        assertEquals(SetupPhase.PLACE_ROVER, FloorSetup().confirmArea().phase)
        assertEquals(SetupPhase.PLACE_DESTINATION, withRover.phase)

        val result = withRover.placeDestination(1f, 0f, -0.5f)
        assertIs<PlacementResult.Accepted>(result)
        assertEquals(SetupPhase.READY, result.setup.phase)
        assertEquals(FloorPoint(1f, -0.5f), result.setup.destination)
    }

    @Test
    fun `destination needs a rover first`() {
        assertEquals(
            PlacementResult.Rejected(PlacementRejection.NO_ROVER),
            FloorSetup().placeDestination(1f, 0f, 1f),
        )
    }

    @Test
    fun `destination on another surface is rejected`() {
        assertEquals(
            PlacementResult.Rejected(PlacementRejection.NOT_SAME_FLOOR),
            withRover.placeDestination(1f, 0.45f, 1f),
        )
        assertEquals(
            PlacementResult.Rejected(PlacementRejection.NOT_SAME_FLOOR),
            withRover.placeDestination(1f, -0.2f, 1f),
        )
    }

    @Test
    fun `small floor height noise is accepted`() {
        assertIs<PlacementResult.Accepted>(withRover.placeDestination(1f, 0.03f, 1f))
    }

    @Test
    fun `destination too close to the rover is rejected`() {
        assertEquals(
            PlacementResult.Rejected(PlacementRejection.TOO_CLOSE),
            withRover.placeDestination(0.1f, 0f, 0.1f),
        )
    }

    @Test
    fun `placing again moves the destination`() {
        val first = withRover.placeDestination(1f, 0f, 0f) as PlacementResult.Accepted
        val second = first.setup.placeDestination(0f, 0f, 2f) as PlacementResult.Accepted
        assertEquals(FloorPoint(0f, 2f), second.setup.destination)
        assertEquals(SetupPhase.READY, second.setup.phase)
    }

    @Test
    fun `reset clears rover and destination`() {
        val ready = (withRover.placeDestination(1f, 0f, 0f) as PlacementResult.Accepted).setup
        assertEquals(FloorSetup(areaConfirmed = true), ready.reset())
        assertEquals(SetupPhase.PLACE_ROVER, ready.reset().phase)
    }

    @Test
    fun `destination outside the play area is rejected`() {
        assertEquals(
            PlacementResult.Rejected(PlacementRejection.OUT_OF_AREA),
            withRover.placeDestination(3f, 0f, 0f),
        )
    }

    @Test
    fun `obstacle is placed and moved on the floor`() {
        val first = withRover.placeObstacle(0.6f, 0f, 0f) as PlacementResult.Accepted
        assertEquals(FloorPoint(0.6f, 0f), first.setup.obstacle)
        val second = first.setup.placeObstacle(0f, 0.02f, 0.8f) as PlacementResult.Accepted
        assertEquals(FloorPoint(0f, 0.8f), second.setup.obstacle)
        assertEquals(null, second.setup.withoutObstacle().obstacle)
    }

    @Test
    fun `obstacle cannot cover the rover or the destination`() {
        assertEquals(
            PlacementResult.Rejected(PlacementRejection.COVERS_ROVER),
            withRover.placeObstacle(0.2f, 0f, 0f),
        )
        val ready = (withRover.placeDestination(1f, 0f, 0f) as PlacementResult.Accepted).setup
        assertEquals(
            PlacementResult.Rejected(PlacementRejection.COVERS_DESTINATION),
            ready.placeObstacle(1.1f, 0f, 0.1f),
        )
    }

    @Test
    fun `obstacle needs a rover and the same floor`() {
        assertEquals(
            PlacementResult.Rejected(PlacementRejection.NO_ROVER),
            FloorSetup().placeObstacle(1f, 0f, 0f),
        )
        assertEquals(
            PlacementResult.Rejected(PlacementRejection.NOT_SAME_FLOOR),
            withRover.placeObstacle(1f, 0.5f, 0f),
        )
    }

    @Test
    fun `destination cannot be placed on the obstacle`() {
        val withObstacle = (withRover.placeObstacle(1f, 0f, 0f) as PlacementResult.Accepted).setup
        assertEquals(
            PlacementResult.Rejected(PlacementRejection.BLOCKED_BY_OBSTACLE),
            withObstacle.placeDestination(1.1f, 0f, 0f),
        )
    }

    @Test
    fun `blocked route is reported only when ready`() {
        val ready = (withRover.placeDestination(1f, 0f, 0f) as PlacementResult.Accepted).setup
        assertEquals(
            SetupHint.NO_ROUTE,
            setupHint(ready, cameraTracking = true, anchorStopped = false, floorDetected = true, aimingAtFloor = true, routeBlocked = true),
        )
        assertEquals(
            SetupHint.TRACKING_LOST,
            setupHint(ready, cameraTracking = false, anchorStopped = false, floorDetected = true, aimingAtFloor = true, routeBlocked = true),
        )
    }

    @Test
    fun `the rover can only be placed after confirming the area`() {
        assertEquals(FloorSetup(), FloorSetup().withRover())
        assertTrue(FloorSetup().confirmArea().withRover().roverPlaced)
    }

    @Test
    fun `restarting keeps the confirmed area`() {
        val ready = (withRover.placeDestination(1f, 0f, 0f) as PlacementResult.Accepted).setup
        assertTrue(ready.reset().areaConfirmed)
        assertEquals(SetupPhase.PLACE_ROVER, ready.reset().phase)
    }

    @Test
    fun `a detected floor asks for confirmation before anything else`() {
        val unconfirmed = FloorSetup()
        assertEquals(
            SetupHint.SCAN_FLOOR,
            setupHint(unconfirmed, cameraTracking = true, anchorStopped = false, floorDetected = false, aimingAtFloor = false),
        )
        assertEquals(
            SetupHint.CONFIRM_AREA,
            setupHint(unconfirmed, cameraTracking = true, anchorStopped = false, floorDetected = true, aimingAtFloor = false),
        )
        assertEquals(
            SetupHint.TRACKING_LOST,
            setupHint(unconfirmed, cameraTracking = false, anchorStopped = false, floorDetected = true, aimingAtFloor = true),
        )
    }

    private fun hint(
        setup: FloorSetup = FloorSetup().confirmArea(),
        cameraTracking: Boolean = true,
        anchorStopped: Boolean = false,
        floorDetected: Boolean = true,
        aimingAtFloor: Boolean = true,
    ) = setupHint(setup, cameraTracking, anchorStopped, floorDetected, aimingAtFloor)

    @Test
    fun `placement hints follow the phase`() {
        assertEquals(SetupHint.TAP_TO_PLACE_ROVER, hint())
        assertEquals(SetupHint.TAP_TO_PLACE_DESTINATION, hint(setup = withRover))
        val ready = (withRover.placeDestination(1f, 0f, 0f) as PlacementResult.Accepted).setup
        assertEquals(SetupHint.READY, hint(setup = ready, aimingAtFloor = false))
    }

    @Test
    fun `floor must be found and aimed at before placing`() {
        assertEquals(SetupHint.SCAN_FLOOR, hint(floorDetected = false, aimingAtFloor = false))
        assertEquals(SetupHint.AIM_AT_FLOOR, hint(aimingAtFloor = false))
        assertEquals(SetupHint.AIM_AT_FLOOR, hint(setup = withRover, aimingAtFloor = false))
    }

    @Test
    fun `lost tracking wins over placement hints`() {
        assertEquals(SetupHint.TRACKING_LOST, hint(cameraTracking = false))
        assertEquals(SetupHint.TRACKING_LOST, hint(setup = withRover, cameraTracking = false))
    }

    @Test
    fun `stopped anchor asks to start over, even while tracking is lost`() {
        assertEquals(SetupHint.ANCHOR_LOST, hint(setup = withRover, anchorStopped = true))
        assertEquals(
            SetupHint.ANCHOR_LOST,
            hint(setup = withRover, anchorStopped = true, cameraTracking = false),
        )
    }

    @Test
    fun `stopped anchor is ignored before the rover is placed`() {
        assertEquals(SetupHint.TAP_TO_PLACE_ROVER, hint(anchorStopped = true))
    }
}
