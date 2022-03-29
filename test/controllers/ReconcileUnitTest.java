/* Copyright 2022 Fabian Steeg, hbz. Licensed under the Eclipse Public License 1.0 */

package controllers;

import static org.hamcrest.Matchers.equalToIgnoringWhiteSpace;
import static org.junit.Assert.assertThat;

import org.junit.Test;

@SuppressWarnings("javadoc")
public class ReconcileUnitTest {

	@Test
	public void preprocessReconciliationQuery_removeSpecialCharacters() {
		assertThat(Reconcile.preprocess("Conference +=<>(){}[]^ (1997 : Kyoto : Japan)"),
				equalToIgnoringWhiteSpace("Conference 1997 Kyoto Japan"));
	}

	@Test
	public void preprocessReconciliationQuery_supportInclusiveRanges() {
		assertThat(Reconcile.preprocess("[1920 TO 1950]"), equalToIgnoringWhiteSpace("[1920 TO 1950]"));
	}

	@Test
	public void preprocessReconciliationQuery_supportExclusiveRanges() {
		assertThat(Reconcile.preprocess("{2010 TO 2021}"), equalToIgnoringWhiteSpace("{2010 TO 2021}"));
	}

	@Test
	public void preprocessReconciliationQuery_supportGroupingWithBooleanOperators() {
		assertThat(Reconcile.preprocess("(Müller OR Meier) AND Michael"),
				equalToIgnoringWhiteSpace("(Müller OR Meier) AND Michael"));
	}
}