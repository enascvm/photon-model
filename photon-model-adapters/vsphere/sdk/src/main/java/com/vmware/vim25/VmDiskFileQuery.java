
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for VmDiskFileQuery complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="VmDiskFileQuery"&gt;
 *   &lt;complexContent&gt;
 *     &lt;extension base="{urn:vim25}FileQuery"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="filter" type="{urn:vim25}VmDiskFileQueryFilter" minOccurs="0"/&gt;
 *         &lt;element name="details" type="{urn:vim25}VmDiskFileQueryFlags" minOccurs="0"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/extension&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "VmDiskFileQuery", propOrder = {
    "filter",
    "details"
})
public class VmDiskFileQuery
    extends FileQuery
{

    protected VmDiskFileQueryFilter filter;
    protected VmDiskFileQueryFlags details;

    /**
     * Gets the value of the filter property.
     * 
     * @return
     *     possible object is
     *     {@link VmDiskFileQueryFilter }
     *     
     */
    public VmDiskFileQueryFilter getFilter() {
        return filter;
    }

    /**
     * Sets the value of the filter property.
     * 
     * @param value
     *     allowed object is
     *     {@link VmDiskFileQueryFilter }
     *     
     */
    public void setFilter(VmDiskFileQueryFilter value) {
        this.filter = value;
    }

    /**
     * Gets the value of the details property.
     * 
     * @return
     *     possible object is
     *     {@link VmDiskFileQueryFlags }
     *     
     */
    public VmDiskFileQueryFlags getDetails() {
        return details;
    }

    /**
     * Sets the value of the details property.
     * 
     * @param value
     *     allowed object is
     *     {@link VmDiskFileQueryFlags }
     *     
     */
    public void setDetails(VmDiskFileQueryFlags value) {
        this.details = value;
    }

}
