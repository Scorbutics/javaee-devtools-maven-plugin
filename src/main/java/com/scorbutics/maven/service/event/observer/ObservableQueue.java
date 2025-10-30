package com.scorbutics.maven.service.event.observer;

import java.nio.file.*;
import java.util.*;

public class ObservableQueue<T>  {
	protected final Set<T> observers = new HashSet<>();

	public void subscribe( final T observer) {
		observers.add(observer);
	}

	public void unsubscribe( final T observer) {
		observers.remove(observer);
	}


}