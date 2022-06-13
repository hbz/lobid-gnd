/* Copyright 2022 Fabian Steeg, hbz. Licensed under the Eclipse Public License 1.0 */

package controllers;

import static org.hamcrest.Matchers.equalToIgnoringWhiteSpace;
import static org.junit.Assert.assertThat;

import org.junit.BeforeClass;
import org.junit.Test;

import modules.IndexTest;

@SuppressWarnings("javadoc")
public class ReconcileUnitTest extends IndexTest {

	private static Reconcile reconcile;

	@BeforeClass
	public static void setIndex() {
		reconcile = new Reconcile();
		reconcile.index = index;
	}

	@Test
	public void preprocessReconciliationQuery_removeSpecialCharacters() {
		assertThat(reconcile.preprocess("Conference +=<>(){}[]^ (1997 : Kyoto : Japan)"),
				equalToIgnoringWhiteSpace("Conference 1997 Kyoto Japan"));
	}

	@Test
	public void preprocessReconciliationQuery_supportInclusiveRanges() {
		assertThat(reconcile.preprocess("[1920 TO 1950]"), equalToIgnoringWhiteSpace("[1920 TO 1950]"));
	}

	@Test
	public void preprocessReconciliationQuery_supportExclusiveRanges() {
		assertThat(reconcile.preprocess("{2010 TO 2021}"), equalToIgnoringWhiteSpace("{2010 TO 2021}"));
	}

	@Test
	public void preprocessReconciliationQuery_supportGroupingWithBooleanOperators() {
		assertThat(reconcile.preprocess("(Müller OR Meier) AND Michael"),
				equalToIgnoringWhiteSpace("(Müller OR Meier) AND Michael"));
	}

	@Test
	public void preprocessReconciliationQuery_supportIncludeExclude() {
		assertThat(reconcile.preprocess("+Müller +Meier -Michael"),
				equalToIgnoringWhiteSpace("+Müller +Meier -Michael"));
	}
}
