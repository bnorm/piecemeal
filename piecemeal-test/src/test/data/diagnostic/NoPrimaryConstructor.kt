import dev.bnorm.piecemeal.Piecemeal

@Piecemeal
<!PIECEMEAL_NO_PRIMARY_CONSTRUCTOR!>class Test<!> {
  constructor(i: Int)
  constructor(s: String)
}
