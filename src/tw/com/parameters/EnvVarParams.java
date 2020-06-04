package tw.com.parameters;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.cloudformation.model.Parameter;
import software.amazon.awssdk.services.cloudformation.model.TemplateParameter;
import tw.com.entity.ProjectAndEnv;
import tw.com.exceptions.InvalidStackParameterException;

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

public class EnvVarParams extends PopulatesParameters {
	private static final Logger logger = LoggerFactory.getLogger(EnvVarParams.class);

	@Override
	public void addParameters(Collection<Parameter> result,
							  List<TemplateParameter> declaredParameters, ProjectAndEnv projAndEnv, ProvidesZones providesZones)
			throws InvalidStackParameterException {
		List<String> toPopulate = findParametersToFill(declaredParameters);
		populateFromEnv(result, toPopulate);
	}
	
	private List<String> findParametersToFill(List<TemplateParameter> declaredParameters) {
		List<String> results = new LinkedList<>();
		for(TemplateParameter candidate : declaredParameters) {
			if (shouldPopulateFor(candidate)) {
				results.add(candidate.parameterKey());
			}
		}
		return results;
	}
	
	private boolean shouldPopulateFor(TemplateParameter candidate) {
		if (candidate.description()==null) {
			return false;
		}
		return candidate.description().equals(PopulatesParameters.ENV_TAG);
	}

	private void populateFromEnv(Collection<Parameter> result,
			List<String> toPopulate) throws InvalidStackParameterException {
		for(String name : toPopulate) {
			logger.info("Attempt to populate parameters from environmental variable: " + name);
			String value = System.getenv(name);
			if (value==null) {
				logger.error("Environment variable not set, name was " + name);
				throw new InvalidStackParameterException(name);
			}
			result.add(Parameter.builder().parameterKey(name).parameterValue(value).build());
		}
		
	}


}
