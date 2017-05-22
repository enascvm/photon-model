
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for OpaqueNetworkCapability complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="OpaqueNetworkCapability"&gt;
 *   &lt;complexContent&gt;
 *     &lt;extension base="{urn:vim25}DynamicData"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="networkReservationSupported" type="{http://www.w3.org/2001/XMLSchema}boolean"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/extension&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "OpaqueNetworkCapability", propOrder = {
    "networkReservationSupported"
})
public class OpaqueNetworkCapability
    extends DynamicData
{

    protected boolean networkReservationSupported;

    /**
     * Gets the value of the networkReservationSupported property.
     * 
     */
    public boolean isNetworkReservationSupported() {
        return networkReservationSupported;
    }

    /**
     * Sets the value of the networkReservationSupported property.
     * 
     */
    public void setNetworkReservationSupported(boolean value) {
        this.networkReservationSupported = value;
    }

}
