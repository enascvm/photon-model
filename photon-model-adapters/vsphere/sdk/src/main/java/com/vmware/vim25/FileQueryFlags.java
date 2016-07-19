
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for FileQueryFlags complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="FileQueryFlags"&gt;
 *   &lt;complexContent&gt;
 *     &lt;extension base="{urn:vim25}DynamicData"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="fileType" type="{http://www.w3.org/2001/XMLSchema}boolean"/&gt;
 *         &lt;element name="fileSize" type="{http://www.w3.org/2001/XMLSchema}boolean"/&gt;
 *         &lt;element name="modification" type="{http://www.w3.org/2001/XMLSchema}boolean"/&gt;
 *         &lt;element name="fileOwner" type="{http://www.w3.org/2001/XMLSchema}boolean" minOccurs="0"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/extension&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "FileQueryFlags", propOrder = {
    "fileType",
    "fileSize",
    "modification",
    "fileOwner"
})
public class FileQueryFlags
    extends DynamicData
{

    protected boolean fileType;
    protected boolean fileSize;
    protected boolean modification;
    protected Boolean fileOwner;

    /**
     * Gets the value of the fileType property.
     * 
     */
    public boolean isFileType() {
        return fileType;
    }

    /**
     * Sets the value of the fileType property.
     * 
     */
    public void setFileType(boolean value) {
        this.fileType = value;
    }

    /**
     * Gets the value of the fileSize property.
     * 
     */
    public boolean isFileSize() {
        return fileSize;
    }

    /**
     * Sets the value of the fileSize property.
     * 
     */
    public void setFileSize(boolean value) {
        this.fileSize = value;
    }

    /**
     * Gets the value of the modification property.
     * 
     */
    public boolean isModification() {
        return modification;
    }

    /**
     * Sets the value of the modification property.
     * 
     */
    public void setModification(boolean value) {
        this.modification = value;
    }

    /**
     * Gets the value of the fileOwner property.
     * 
     * @return
     *     possible object is
     *     {@link Boolean }
     *     
     */
    public Boolean isFileOwner() {
        return fileOwner;
    }

    /**
     * Sets the value of the fileOwner property.
     * 
     * @param value
     *     allowed object is
     *     {@link Boolean }
     *     
     */
    public void setFileOwner(Boolean value) {
        this.fileOwner = value;
    }

}
