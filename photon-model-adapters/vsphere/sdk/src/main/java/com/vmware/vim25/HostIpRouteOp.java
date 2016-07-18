
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for HostIpRouteOp complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="HostIpRouteOp"&gt;
 *   &lt;complexContent&gt;
 *     &lt;extension base="{urn:vim25}DynamicData"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="changeOperation" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
 *         &lt;element name="route" type="{urn:vim25}HostIpRouteEntry"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/extension&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "HostIpRouteOp", propOrder = {
    "changeOperation",
    "route"
})
public class HostIpRouteOp
    extends DynamicData
{

    @XmlElement(required = true)
    protected String changeOperation;
    @XmlElement(required = true)
    protected HostIpRouteEntry route;

    /**
     * Gets the value of the changeOperation property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getChangeOperation() {
        return changeOperation;
    }

    /**
     * Sets the value of the changeOperation property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setChangeOperation(String value) {
        this.changeOperation = value;
    }

    /**
     * Gets the value of the route property.
     * 
     * @return
     *     possible object is
     *     {@link HostIpRouteEntry }
     *     
     */
    public HostIpRouteEntry getRoute() {
        return route;
    }

    /**
     * Sets the value of the route property.
     * 
     * @param value
     *     allowed object is
     *     {@link HostIpRouteEntry }
     *     
     */
    public void setRoute(HostIpRouteEntry value) {
        this.route = value;
    }

}
