
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for anonymous complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType&gt;
 *   &lt;complexContent&gt;
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="returnval" type="{urn:vim25}IscsiMigrationDependency"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/restriction&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "", propOrder = {
    "returnval"
})
@XmlRootElement(name = "QueryMigrationDependenciesResponse")
public class QueryMigrationDependenciesResponse {

    @XmlElement(required = true)
    protected IscsiMigrationDependency returnval;

    /**
     * Gets the value of the returnval property.
     * 
     * @return
     *     possible object is
     *     {@link IscsiMigrationDependency }
     *     
     */
    public IscsiMigrationDependency getReturnval() {
        return returnval;
    }

    /**
     * Sets the value of the returnval property.
     * 
     * @param value
     *     allowed object is
     *     {@link IscsiMigrationDependency }
     *     
     */
    public void setReturnval(IscsiMigrationDependency value) {
        this.returnval = value;
    }

}
