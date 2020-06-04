package tw.com.entity;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.cloudformation.model.Stack;
import software.amazon.awssdk.services.cloudformation.model.StackStatus;
import tw.com.providers.CFNClient;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

public class StackEntry {
	private static final Logger logger = LoggerFactory.getLogger(StackEntry.class);

	private EnvironmentTag environmentTag;
	private Stack stack;
	private String project;

    private Optional<Integer> buildNumber = Optional.empty();
    private Optional<Integer> index = Optional.empty();
    private Optional<CFNClient.DriftStatus> driftStatus = Optional.empty();
	private Set<Integer> updateIndex = new HashSet<>();

	public StackEntry(String project, EnvironmentTag environmentTag, Stack stack) {
		this.environmentTag = environmentTag;
		this.stack = stack;
		this.project = project;
	}
	
	@Override
	public String toString() {
		StringBuilder stringBuilder = new StringBuilder();
		stringBuilder.append(String.format("StackEntry [env=%s, project=%s, stackName=%s",
						environmentTag.getEnv(), project, stack.stackName()));
		if (buildNumber.isPresent()) {
			stringBuilder.append(String.format(" buildNumber='%s'", buildNumber.get()));
		}
		if (index.isPresent()) {
			stringBuilder.append(String.format(" index='%s'", index.get()));
		}
		if (driftStatus.isPresent()) {
			stringBuilder.append(String.format(" index='%s'", driftStatus.get()));
		}

		stringBuilder.append("]");
		return stringBuilder.toString();
	}

	public EnvironmentTag getEnvTag() {
		return environmentTag;
	}

	public Stack getStack() {
		return stack;
	}

	public Integer getBuildNumber() {
        return buildNumber.get();
	}

	public CFNClient.DriftStatus getDriftStatus() {
		return driftStatus.get();
	}
	
	public StackEntry setBuildNumber(Integer buildNumber) {
		this.buildNumber = Optional.of(buildNumber);
		return this;
	}

	public StackEntry setIndex(int index) {
		this.index = Optional.of(index);
        return this;
	}
	
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result
				+ ((environmentTag == null) ? 0 : environmentTag.hashCode());
		result = prime * result + ((stack == null) ? 0 : stack.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		StackEntry other = (StackEntry) obj;
		if (environmentTag == null) {
			if (other.environmentTag != null)
				return false;
		} else if (!environmentTag.equals(other.environmentTag))
			return false;
		if (stack == null) {
			if (other.stack != null)
				return false;
		} else if (!stack.equals(other.stack))
			return false;
		return true;
	}

	public boolean isLive() {
		return stack.stackStatus().equals(StackStatus.CREATE_COMPLETE);
	}

	public String getStackName() {
		return stack.stackName();
	}

	public String getProject() {
		return project;
	}

	public String getBaseName() {
		String fullName = stack.stackName();
		String withoutProject = fullName.replace(project, "");
		
		String withoutBuild = withoutProject;
		if (buildNumber.isPresent()) {
			withoutBuild =  withoutProject.replace(buildNumber.get().toString(), "");
		}
		
		String basename = withoutBuild.replace(environmentTag.getEnv(), "");
		logger.debug("basename is " + basename);
		return basename;
	}

	public boolean hasBuildNumber() {
		return buildNumber.isPresent();
	}

    public Integer getIndex() {
        return index.get();
    }

    public boolean hasIndex() {
        return index.isPresent();
    }

	public StackEntry setUpdateIndex(Set<Integer> updateIndex) {
		this.updateIndex = updateIndex;
		return this;
	}

	public boolean hasUpdateIndex() {
		return  !updateIndex.isEmpty();
	}

	public Set<Integer> getUpdateIndex() {
		return updateIndex;
	}

	public void setDriftStatus(CFNClient.DriftStatus driftStatus) {
		this.driftStatus = Optional.of(driftStatus);
	}
}
