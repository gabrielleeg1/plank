// This is a generated file. Not intended for manual editing.
package com.lorenzoog.plank.intellijplugin.psi;

import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;

public interface PlankIfExpr extends PsiElement {

  @Nullable
  PlankElseBranch getElseBranch();

  @NotNull
  PlankExpr getExpr();

  @NotNull
  PlankThenBranch getThenBranch();

}