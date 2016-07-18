
package com.vmware.vim25;

import java.util.ArrayList;
import java.util.List;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for ImportOperationBulkFault complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="ImportOperationBulkFault"&gt;
 *   &lt;complexContent&gt;
 *     &lt;extension base="{urn:vim25}DvsFault"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="importFaults" type="{urn:vim25}ImportOperationBulkFaultFaultOnImport" maxOccurs="unbounded"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/extension&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "ImportOperationBulkFault", propOrder = {
    "importFaults"
})
public class ImportOperationBulkFault
    extends DvsFault
{

    @XmlElement(required = true)
    protected List<ImportOperationBulkFaultFaultOnImport> importFaults;

    /**
     * Gets the value of the importFaults property.
     * 
     * <p>
     * This accessor method returns a reference to the live list,
     * not a snapshot. Therefore any modification you make to the
     * returned list will be present inside the JAXB object.
     * This is why there is not a <CODE>set</CODE> method for the importFaults property.
     * 
     * <p>
     * For example, to add a new item, do as follows:
     * <pre>
     *    getImportFaults().add(newItem);
     * </pre>
     * 
     * 
     * <p>
     * Objects of the following type(s) are allowed in the list
     * {@link ImportOperationBulkFaultFaultOnImport }
     * 
     * 
     */
    public List<ImportOperationBulkFaultFaultOnImport> getImportFaults() {
        if (importFaults == null) {
            importFaults = new ArrayList<ImportOperationBulkFaultFaultOnImport>();
        }
        return this.importFaults;
    }

}
