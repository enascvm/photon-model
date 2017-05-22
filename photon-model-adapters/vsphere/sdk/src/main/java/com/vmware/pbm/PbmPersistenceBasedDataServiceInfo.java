
package com.vmware.pbm;

import java.util.ArrayList;
import java.util.List;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for PbmPersistenceBasedDataServiceInfo complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="PbmPersistenceBasedDataServiceInfo"&gt;
 *   &lt;complexContent&gt;
 *     &lt;extension base="{urn:pbm}PbmLineOfServiceInfo"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="compatiblePersistenceSchemaNamespace" type="{http://www.w3.org/2001/XMLSchema}string" maxOccurs="unbounded" minOccurs="0"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/extension&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "PbmPersistenceBasedDataServiceInfo", propOrder = {
    "compatiblePersistenceSchemaNamespace"
})
public class PbmPersistenceBasedDataServiceInfo
    extends PbmLineOfServiceInfo
{

    protected List<String> compatiblePersistenceSchemaNamespace;

    /**
     * Gets the value of the compatiblePersistenceSchemaNamespace property.
     * 
     * <p>
     * This accessor method returns a reference to the live list,
     * not a snapshot. Therefore any modification you make to the
     * returned list will be present inside the JAXB object.
     * This is why there is not a <CODE>set</CODE> method for the compatiblePersistenceSchemaNamespace property.
     * 
     * <p>
     * For example, to add a new item, do as follows:
     * <pre>
     *    getCompatiblePersistenceSchemaNamespace().add(newItem);
     * </pre>
     * 
     * 
     * <p>
     * Objects of the following type(s) are allowed in the list
     * {@link String }
     * 
     * 
     */
    public List<String> getCompatiblePersistenceSchemaNamespace() {
        if (compatiblePersistenceSchemaNamespace == null) {
            compatiblePersistenceSchemaNamespace = new ArrayList<String>();
        }
        return this.compatiblePersistenceSchemaNamespace;
    }

}
