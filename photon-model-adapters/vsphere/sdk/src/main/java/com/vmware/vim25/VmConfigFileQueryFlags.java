
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for VmConfigFileQueryFlags complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="VmConfigFileQueryFlags"&gt;
 *   &lt;complexContent&gt;
 *     &lt;extension base="{urn:vim25}DynamicData"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="configVersion" type="{http://www.w3.org/2001/XMLSchema}boolean"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/extension&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "VmConfigFileQueryFlags", propOrder = {
    "configVersion"
})
public class VmConfigFileQueryFlags
    extends DynamicData
{

    protected boolean configVersion;

    /**
     * Gets the value of the configVersion property.
     * 
     */
    public boolean isConfigVersion() {
        return configVersion;
    }

    /**
     * Sets the value of the configVersion property.
     * 
     */
    public void setConfigVersion(boolean value) {
        this.configVersion = value;
    }

}
