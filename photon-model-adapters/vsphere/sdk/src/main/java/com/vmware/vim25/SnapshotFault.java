
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlSeeAlso;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for SnapshotFault complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="SnapshotFault"&gt;
 *   &lt;complexContent&gt;
 *     &lt;extension base="{urn:vim25}VimFault"&gt;
 *       &lt;sequence&gt;
 *       &lt;/sequence&gt;
 *     &lt;/extension&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "SnapshotFault")
@XmlSeeAlso({
    ApplicationQuiesceFault.class,
    FilesystemQuiesceFault.class,
    MemorySnapshotOnIndependentDisk.class,
    MultipleSnapshotsNotSupported.class,
    SnapshotDisabled.class,
    SnapshotIncompatibleDeviceInVm.class,
    SnapshotLocked.class,
    SnapshotNoChange.class,
    TooManySnapshotLevels.class
})
public class SnapshotFault
    extends VimFault
{


}
