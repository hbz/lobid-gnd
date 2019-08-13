package apps;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.junit.runners.Suite.SuiteClasses;

import modules.IndexQueryTest;

@RunWith(Suite.class)
@SuiteClasses({ ConvertTest.class, IndexAppTest.class, IndexQueryTest.class })
public class DataWorkflowTests {

}
