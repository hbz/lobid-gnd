package modules;

import play.api.Configuration;
import play.api.Environment;
import play.api.inject.Binding;
import play.api.inject.Module;
import scala.collection.Seq;

public class AuthoritiesModule extends Module {
	public Seq<Binding<?>> bindings(Environment environment, Configuration configuration) {
		return seq(bind(IndexComponent.class).to(EmbeddedIndex.class));
	}
}