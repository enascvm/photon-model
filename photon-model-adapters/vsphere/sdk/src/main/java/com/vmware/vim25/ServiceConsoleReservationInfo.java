
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for ServiceConsoleReservationInfo complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="ServiceConsoleReservationInfo"&gt;
 *   &lt;complexContent&gt;
 *     &lt;extension base="{urn:vim25}DynamicData"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="serviceConsoleReservedCfg" type="{http://www.w3.org/2001/XMLSchema}long"/&gt;
 *         &lt;element name="serviceConsoleReserved" type="{http://www.w3.org/2001/XMLSchema}long"/&gt;
 *         &lt;element name="unreserved" type="{http://www.w3.org/2001/XMLSchema}long"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/extension&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "ServiceConsoleReservationInfo", propOrder = {
    "serviceConsoleReservedCfg",
    "serviceConsoleReserved",
    "unreserved"
})
public class ServiceConsoleReservationInfo
    extends DynamicData
{

    protected long serviceConsoleReservedCfg;
    protected long serviceConsoleReserved;
    protected long unreserved;

    /**
     * Gets the value of the serviceConsoleReservedCfg property.
     * 
     */
    public long getServiceConsoleReservedCfg() {
        return serviceConsoleReservedCfg;
    }

    /**
     * Sets the value of the serviceConsoleReservedCfg property.
     * 
     */
    public void setServiceConsoleReservedCfg(long value) {
        this.serviceConsoleReservedCfg = value;
    }

    /**
     * Gets the value of the serviceConsoleReserved property.
     * 
     */
    public long getServiceConsoleReserved() {
        return serviceConsoleReserved;
    }

    /**
     * Sets the value of the serviceConsoleReserved property.
     * 
     */
    public void setServiceConsoleReserved(long value) {
        this.serviceConsoleReserved = value;
    }

    /**
     * Gets the value of the unreserved property.
     * 
     */
    public long getUnreserved() {
        return unreserved;
    }

    /**
     * Sets the value of the unreserved property.
     * 
     */
    public void setUnreserved(long value) {
        this.unreserved = value;
    }

}
