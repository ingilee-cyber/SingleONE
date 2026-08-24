package com.singleone.backend.simulation;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** PRD 10장 Media Planning Simulation API. */
@RestController
public class SimulationController {

	private final SimulationService simulationService;

	public SimulationController(SimulationService simulationService) {
		this.simulationService = simulationService;
	}

	@PostMapping("/api/v1/projects/{projectId}/simulation")
	public SimulationResult simulate(@PathVariable Long projectId, @RequestBody SimulationRequest request) {
		return simulationService.simulate(projectId, request);
	}

}
