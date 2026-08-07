package com.bragincad.dwg

import com.bragincad.model.DrawingModel

interface DwgReader { fun read(path: String): DrawingModel }

