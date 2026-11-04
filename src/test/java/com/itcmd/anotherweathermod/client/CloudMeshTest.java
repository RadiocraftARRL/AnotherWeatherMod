package com.itcmd.anotherweathermod.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CloudMeshTest {

	/** Every kind and fade role fits the alpha byte and reads back as the shader reads it. */
	@Test
	void marksReadBack() {
		for(int kind : new int[] { CloudMesh.FAIR, CloudMesh.STORM, CloudMesh.GREY })
			for(int role : new int[] { CloudMesh.STEADY, CloudMesh.IN, CloudMesh.OUT }) {
				int mark = CloudMesh.mark(kind, role);
				assertTrue(mark >= 0 && mark < 256);
				int readKind = (int)(mark / 255.0f * 255.0f + 0.5f) / 4;
				assertEquals(kind, readKind);
				assertEquals(role, mark - readKind * 4);
			}
	}

	/** A tile fades only when a cell's cloud changes or its detail does; empty staying empty is no change. */
	@Test
	void changes() {
		int none = CloudCover.NONE;
		assertFalse(CloudMesh.changes(new int[] { none, 2 }, new int[] { none, 2 }, false, false));
		assertTrue(CloudMesh.changes(new int[] { none, 3 }, new int[] { none, 2 }, false, false), "a cumulus grew");
		assertTrue(CloudMesh.changes(new int[] { none, none }, new int[] { none, 2 }, false, false), "a cumulus went");
		assertTrue(CloudMesh.changes(new int[] { none, 2 }, new int[] { none, 2 }, true, false), "near to far");
	}

	/** A cell fade runs from 0 to 1 over its time and stays at 1. */
	@Test
	void cellFadeRuns() {
		assertEquals(0, CloudRenderer.cellFade(0));
		assertEquals(0.5f, CloudRenderer.cellFade((long)(CloudRenderer.CELL_FADE_SECONDS * 0.5e9)), 1e-4);
		assertEquals(1, CloudRenderer.cellFade((long)(CloudRenderer.CELL_FADE_SECONDS * 3e9)));
	}
}
