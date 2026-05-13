package de.muenchen.mcmp.clients.netapp.ontap;

import de.muenchen.mcmp.ontap.OntapSvm;

record SvmVolumePair(OntapSvm svm, OntapDTO.VolumeData volumeData) {
}
