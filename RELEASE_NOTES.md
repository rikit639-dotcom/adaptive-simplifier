### Release Notes: v3.1.0

**English**
* Added simplification of start/end junction nodes on closed ways with protection against polygon degeneration.
* Fixed priority queue ordering during simplification to accurately preserve corners.
* Switched threshold and geometry calculations to true meters on the ground, independent of latitude and map projection.
* Fixed coordinate tracking of moved author nodes across consecutive simplification and merge passes.
* Fixed self-intersection detection for non-adjacent coincident nodes.

**Russian**
* Добавлено упрощение стыковой (начальной/конечной) точки на замкнутых линиях с защитой от вырождения контуров.
* Исправлен порядок удаления точек в очереди приоритетов для точного сохранения углов.
* Расчёты порогов и геометрии переведены в реальные метры на местности независимо от широты и проекции карты.
* Исправлен учёт обновлённых координат перенесённых авторских точек на последующих этапах упрощения и слияния.
* Улучшено выявление самопересечений для несмежных совпадающих точек.