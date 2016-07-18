
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlSeeAlso;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for FileFault complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="FileFault"&gt;
 *   &lt;complexContent&gt;
 *     &lt;extension base="{urn:vim25}VimFault"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="file" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/extension&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "FileFault", propOrder = {
    "file"
})
@XmlSeeAlso({
    CannotAccessFile.class,
    CannotCreateFile.class,
    CannotDeleteFile.class,
    DirectoryNotEmpty.class,
    FileAlreadyExists.class,
    FileLocked.class,
    FileNameTooLong.class,
    FileNotFound.class,
    FileNotWritable.class,
    FileTooLarge.class,
    IncorrectFileType.class,
    NetworkCopyFault.class,
    NoDiskSpace.class,
    NotADirectory.class,
    NotAFile.class,
    TooManyConcurrentNativeClones.class,
    TooManyNativeCloneLevels.class,
    TooManyNativeClonesOnFile.class
})
public class FileFault
    extends VimFault
{

    @XmlElement(required = true)
    protected String file;

    /**
     * Gets the value of the file property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getFile() {
        return file;
    }

    /**
     * Sets the value of the file property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setFile(String value) {
        this.file = value;
    }

}
