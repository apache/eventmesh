package org.apache.eventmesh.store.api.openschema.common;

public enum CompatibilityLevel {
	  NONE,
	  BACKWARD,
	  BACKWARD_TRANSITIVE,
	  FORWARD,
	  FORWARD_TRANSITIVE,
	  FULL,
	  FULL_TRANSITIVE;

	  public final String name;

	  private CompatibilityLevel() {
	    this.name = name();
	  }

	  public static CompatibilityLevel forName(String name) {
	    if (name == null) {
	      return null;
	    }

	    name = name.toUpperCase();
	    if (NONE.name.equals(name)) {
	      return NONE;
	    } else if (BACKWARD.name.equals(name)) {
	      return BACKWARD;
	    } else if (FORWARD.name.equals(name)) {
	      return FORWARD;
	    } else if (FULL.name.equals(name)) {
	      return FULL;
	    } else if (BACKWARD_TRANSITIVE.name.equals(name)) {
	      return BACKWARD_TRANSITIVE;
	    } else if (FORWARD_TRANSITIVE.name.equals(name)) {
	      return FORWARD_TRANSITIVE;
	    } else if (FULL_TRANSITIVE.name.equals(name)) {
	      return FULL_TRANSITIVE;
	    } else {
	      return null;
	    }
	  }
}
