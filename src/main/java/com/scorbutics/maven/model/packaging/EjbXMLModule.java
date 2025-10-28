package com.scorbutics.maven.model.packaging;


import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "ejbModule")
@XmlAccessorType(XmlAccessType.FIELD)
public class EjbXMLModule
		extends JarXMLModule {
	@Override
	public Packaging getPackaging() {
		return Packaging.EJB;
	}
}